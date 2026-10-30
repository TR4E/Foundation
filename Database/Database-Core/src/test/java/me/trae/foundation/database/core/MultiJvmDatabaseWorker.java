package me.trae.foundation.database.core;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.holder.LookupTier;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.core.batch.PendingWriteStore;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.driver.PostgresSettings;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.driver.RedisSettings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@UtilityClass
public final class MultiJvmDatabaseWorker {

    public static final EntityProperty<WorkerEntity, String> VALUE = EntityProperty.register(WorkerEntity.class, "value", WorkerEntity::getValue, WorkerEntity::setValue, String.class, true);

    public static void main(final String[] argumentList) throws Exception {
        final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings(argumentList[0], Integer.parseInt(argumentList[1]), argumentList[2], argumentList[3], argumentList[4], Integer.parseInt(argumentList[13])));

        final RedisDriver redisDriver = new RedisDriver(new RedisSettings(argumentList[5], Integer.parseInt(argumentList[6]), argumentList[7].isEmpty() ? null : argumentList[7], Integer.parseInt(argumentList[8]), Duration.ofSeconds(2)));

        postgresDriver.connect();
        redisDriver.connect();

        final CoreDatabase coreDatabase = new CoreDatabase(postgresDriver, redisDriver, null, Duration.ofHours(1), 100, PendingWriteStore.DEFAULT_MAX_PENDING_WRITES);

        final boolean deferred = Boolean.parseBoolean(argumentList[11]);

        final WorkerRepository repository = new WorkerRepository(coreDatabase, argumentList[9], redisDriver, argumentList[12].isEmpty() ? null : argumentList[12]);

        final WorkerHolder holder = new WorkerHolder(repository);

        final UUID id = UUID.fromString(argumentList[10]);
        coreDatabase.start();

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            final WorkerEntity entity = deferred ? null : holder.getById(id).orElseThrow();
            System.out.println("READY|%s".formatted(deferred ? "deferred" : entity.getValue()));
            System.out.flush();

            String command;
            while ((command = reader.readLine()) != null) {
                if (command.equals("STOP")) {
                    break;
                }

                if (command.equals("PIN")) {
                    holder.pin(holder.getById(id).orElseThrow());
                    System.out.println("ACK|pinned");
                    System.out.flush();
                    continue;
                }

                if (command.equals("LOCAL")) {
                    System.out.println("VALUE|%s".formatted(holder.getById(id, EnumSet.of(LookupTier.LOCAL)).map(WorkerEntity::getValue).orElse("missing")));
                    System.out.flush();
                    continue;
                }

                if (command.equals("DISCONNECT")) {
                    redisDriver.disconnect();
                    System.out.println("ACK|disconnected");
                    System.out.flush();
                    continue;
                }

                if (command.equals("RECONNECT")) {
                    redisDriver.connect();
                    final long reconnectDeadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                    while (true) {
                        try {
                            redisDriver.getEntityCoordinator().checkLease();
                            break;
                        } catch (final ConnectionException exception) {
                            if (System.nanoTime() >= reconnectDeadline) {
                                throw exception;
                            }
                            Thread.sleep(1L);
                        }
                    }
                    System.out.println("ACK|reconnected");
                    System.out.flush();
                    continue;
                }

                if (command.startsWith("PREPARE|")) {
                    final String[] parts = command.split("\\|", 3);
                    Thread.ofVirtual().start(() -> {
                        try {
                            while (redisDriver.getCommands().get(parts[2]) == null) {
                                Thread.sleep(1L);
                            }

                            entity.setValue(parts[1]);
                            holder.update(entity);
                            coreDatabase.getBatchQueue().flush();
                            System.out.println("WRITE|%s".formatted(parts[1]));
                            System.out.flush();
                        } catch (final InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    });
                    System.out.println("PREPARED|%s".formatted(parts[1]));
                    System.out.flush();
                    continue;
                }

                if (command.startsWith("CREATE|")) {
                    final String[] parts = command.split("\\|", 3);

                    final WorkerEntity createdEntity = new WorkerEntity(id);
                    createdEntity.setValue(parts[1]);
                    System.out.println("PREPARED|%s".formatted(parts[1]));
                    System.out.flush();

                    Thread.ofVirtual().start(() -> {
                        try {
                            while (redisDriver.getCommands().get(parts[2]) == null) {
                                Thread.sleep(1L);
                            }

                            final CompletableFuture<Boolean> createdFuture = new CompletableFuture<>();
                            try {
                                holder.save(createdEntity, () -> createdFuture.complete(true), () -> createdFuture.complete(false));
                                coreDatabase.getBatchQueue().flush();
                            } catch (final DatabaseException exception) {
                                createdFuture.complete(false);
                            }
                            final boolean accepted = createdFuture.get(5L, TimeUnit.SECONDS);
                            final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                            while (repository.findById(id).isEmpty() && System.nanoTime() < deadline) {
                                Thread.sleep(1L);
                            }
                            System.out.println("CREATED|%s|%s".formatted(accepted ? "accepted" : "rejected", repository.findById(id).map(WorkerEntity::getValue).orElse("missing")));
                            System.out.flush();
                        } catch (final Exception exception) {
                            if (exception instanceof InterruptedException) {
                                Thread.currentThread().interrupt();
                            }
                            throw new IllegalStateException(exception);
                        }
                    });
                    continue;
                }

                if (command.startsWith("READ|")) {
                    final String expectedValue = command.substring("READ|".length());

                    final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                    WorkerEntity current = holder.getById(id).orElse(null);

                    while (current != null && !expectedValue.equals(current.getValue()) && System.nanoTime() < deadline) {
                        Thread.sleep(10L);
                        current = holder.getById(id).orElse(null);
                    }

                    System.out.println("VALUE|%s".formatted(current == null ? "missing" : current.getValue()));
                    System.out.flush();
                }
            }
        } finally {
            coreDatabase.stop();
            redisDriver.disconnect();
            postgresDriver.disconnect();
        }
    }
}