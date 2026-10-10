package me.trae.foundation.database.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.driver.PostgresSettings;
import me.trae.foundation.database.core.holder.AbstractEntityHolder;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.driver.RedisSettings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@UtilityClass
public class MultiJvmDatabaseWorker {

    public static final EntityProperty<WorkerEntity, String> VALUE = EntityProperty.register(WorkerEntity.class, "value", WorkerEntity::getValue, WorkerEntity::setValue, String.class, true);

    public static void main(final String[] argumentList) throws Exception {
        final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings(argumentList[0], Integer.parseInt(argumentList[1]), argumentList[2], argumentList[3], argumentList[4], 4));
        final RedisDriver redisDriver = new RedisDriver(new RedisSettings(argumentList[5], Integer.parseInt(argumentList[6]), argumentList[7].isEmpty() ? null : argumentList[7], Integer.parseInt(argumentList[8]), Duration.ofSeconds(2)));

        postgresDriver.connect();
        redisDriver.connect();

        final CoreDatabase coreDatabase = new CoreDatabase(postgresDriver, redisDriver, null, Duration.ofHours(1), 100);
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

                if (command.equals("DISCONNECT")) {
                    redisDriver.disconnect();
                    System.out.println("ACK|disconnected");
                    System.out.flush();
                    continue;
                }

                if (command.equals("RECONNECT")) {
                    redisDriver.connect();
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
                            holder.save(entity);
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

    @RequiredArgsConstructor
    @Getter
    @Setter
    public static final class WorkerEntity implements Entity {

        private final UUID id;
        private String value;
    }

    public static final class WorkerRepository extends AbstractEntityRepository<WorkerEntity> {

        private final RedisDriver redisDriver;
        private final String countKey;

        public WorkerRepository(final CoreDatabase coreDatabase, final String table) {
            this(coreDatabase, table, null, null);
        }

        public WorkerRepository(final CoreDatabase coreDatabase, final String table, final RedisDriver redisDriver, final String countKey) {
            super(coreDatabase, WorkerEntity.class, table, TenantScope.NONE, MultiJvmDatabaseWorker.class);
            this.redisDriver = redisDriver;
            this.countKey = countKey;
        }

        @Override
        public Optional<WorkerEntity> findById(final UUID id) {
            if (this.countKey != null) {
                this.redisDriver.getCommands().incr(this.countKey);
                try {
                    Thread.sleep(100L);
                } catch (final InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }

            return super.findById(id);
        }
    }

    public static final class WorkerHolder extends AbstractEntityHolder<WorkerEntity> {

        public WorkerHolder(final WorkerRepository repository) {
            super(repository, InstanceMode.MULTI_INSTANCE, Duration.ofMinutes(5), Duration.ofMinutes(5));
        }
    }
}