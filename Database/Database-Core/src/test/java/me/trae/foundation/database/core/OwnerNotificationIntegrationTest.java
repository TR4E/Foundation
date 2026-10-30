package me.trae.foundation.database.core;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.driver.RedisSettings;
import me.trae.foundation.database.storage.redis.RedisInvalidation;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import me.trae.foundation.database.storage.redis.RedisStorage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OwnerNotificationIntegrationTest {

    private static final EntityProperty<TestEntity, String> NAME = EntityProperty.register(TestEntity.class, "name", TestEntity::getName, TestEntity::setName, String.class, true);

    @Test
    void duplicateOutboxDeliveryDoesNotDestroyAnAcknowledgedRefreshFillLock() throws Exception {
        DatabaseTestEnvironment.requirePostgresAndRedis();
        final RedisSettings redisSettings = new RedisSettings(
                System.getProperty("redis.host", "localhost"),
                Integer.getInteger("redis.port", 6379),
                System.getProperty("redis.password"),
                Integer.getInteger("redis.database", 0),
                Duration.ofSeconds(2)
        );
        final RedisDriver sender = new RedisDriver(redisSettings);
        final RedisDriver receiver = new RedisDriver(redisSettings);
        final AtomicInteger loadCount = new AtomicInteger();

        final CountDownLatch loaderStarted = new CountDownLatch(1);
        final CountDownLatch releaseLoader = new CountDownLatch(1);
        try {
            sender.connect();
            receiver.connect();
            final RedisNamespace redisNamespace = RedisNamespace.of("notification_race_%s".formatted(UUID.randomUUID().toString().replace("-", "")));
            final UUID id = UUID.randomUUID();
            final RedisStorage<TestEntity> redisStorage = new RedisStorage<>(receiver, redisNamespace, TestEntity.class, Duration.ofMinutes(1));
            receiver.getEntityCoordinator().bind(redisNamespace.getKey(""), "receiver", entityId -> redisStorage.getOrLoad(entityId, () -> {
                loadCount.incrementAndGet();
                loaderStarted.countDown();
                try {
                    assertTrue(releaseLoader.await(3L, TimeUnit.SECONDS));
                } catch (final InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                final TestEntity testEntity = new TestEntity(entityId);
                testEntity.setName("committed");
                return Optional.of(testEntity);
            }), () -> {}, _ -> {});
            receiver.getEntityCoordinator().admit(redisNamespace.getKey(""), id, "receiver").getReadyFuture().complete(null);
            Thread.sleep(10_500L);
            final RedisInvalidation redisInvalidation = redisNamespace.getInvalidation(id, "source");
            final CompletableFuture<Void> synchronizationFuture = sender.getEntityCoordinator().notifyOwnersNow(redisInvalidation, redisInvalidation.getNotificationId(), true);
            assertTrue(loaderStarted.await(2L, TimeUnit.SECONDS));
            final String generation = sender.getCommands().get(redisNamespace.getKey("cache:generation:%s".formatted(id)));
            receiver.getEntityCoordinator().notifyOwnersNow(redisInvalidation, redisInvalidation.getNotificationId(), false).get(1L, TimeUnit.SECONDS);
            releaseLoader.countDown();

            synchronizationFuture.get(2L, TimeUnit.SECONDS);
            assertEquals(generation, sender.getCommands().get(redisNamespace.getKey("cache:generation:%s".formatted(id))));
            assertEquals("committed", redisStorage.get(id).orElseThrow().getName());
            assertEquals(1, loadCount.get());
            final long receiptTtl = sender.getCommands().pttl(redisNamespace.getKey("cache:notification:%s".formatted(redisInvalidation.getNotificationId())));
            assertTrue(receiptTtl > 0L && receiptTtl <= Duration.ofMinutes(5).toMillis());

            final RedisInvalidation nextInvalidation = redisNamespace.getInvalidation(id, "source");
            sender.getEntityCoordinator().notifyOwnersNow(nextInvalidation, nextInvalidation.getNotificationId(), true).get(2L, TimeUnit.SECONDS);
            assertEquals(Long.parseLong(generation) + 1L, Long.parseLong(sender.getCommands().get(redisNamespace.getKey("cache:generation:%s".formatted(id)))));
            assertEquals(2, loadCount.get());
        } finally {
            releaseLoader.countDown();
            receiver.getEntityCoordinator().close();
            sender.getEntityCoordinator().close();
            receiver.disconnect();
            sender.disconnect();
        }
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class TestEntity implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private String name;
    }
}