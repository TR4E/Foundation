package me.trae.foundation.database.storage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.storage.codec.EntityCodec;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.driver.RedisSettings;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import me.trae.foundation.database.storage.redis.RedisStorage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StorageTest {

    private static final EntityProperty<Wallet, String> OWNER = EntityProperty.register(Wallet.class, "owner", Wallet::getOwner, Wallet::setOwner, String.class, true);
    private static final EntityProperty<Wallet, Long> COINS = EntityProperty.register(Wallet.class, "coins", Wallet::getCoins, Wallet::setCoins, Long.class, true);
    private static final EntityProperty<Wallet, BigDecimal> BALANCE = EntityProperty.register(Wallet.class, "balance", Wallet::getBalance, Wallet::setBalance, BigDecimal.class, true);
    private static final EntityProperty<Wallet, Instant> CREATED_AT = EntityProperty.register(Wallet.class, "createdAt", Wallet::getCreatedAt, Wallet::setCreatedAt, Instant.class, true);
    private static final EntityProperty<Wallet, Rank> RANK = EntityProperty.register(Wallet.class, "rank", Wallet::getRank, Wallet::setRank, Rank.class, true);

    @Test
    void localStorageExpiresUnlessPinned() {
        final LocalStorage<String, String> localStorage = new LocalStorage<>(Duration.ZERO);

        localStorage.put("expired", "1");
        localStorage.pin("pinned", "2");
        localStorage.put("pinned", "3");

        assertTrue(localStorage.get("expired").isEmpty());
        assertEquals(Optional.of("3"), localStorage.get("pinned"));
        assertEquals(List.of("3"), localStorage.getPinnedValues());

        localStorage.unpin("pinned");
        assertTrue(localStorage.get("pinned").isEmpty());
    }

    @Test
    void codecRoundTripsEveryValueType() {
        final EntityCodec<Wallet> entityCodec = new EntityCodec<>(Wallet.class);
        final Wallet wallet = this.createWallet("trae", 1_000_000_000_000L);
        wallet.setBalance(new BigDecimal("12.50"));
        wallet.setCreatedAt(Instant.parse("2026-10-06T10:15:30.123456789Z"));
        wallet.setRank(Rank.ADMIN);

        final Map<String, String> fieldMap = entityCodec.encode(wallet);
        final Wallet decoded = entityCodec.decode(wallet.getId(), fieldMap);

        assertEquals("ADMIN", fieldMap.get(RANK.getName()));
        assertEquals(wallet.getOwner(), decoded.getOwner());
        assertEquals(wallet.getCoins(), decoded.getCoins());
        assertEquals(wallet.getBalance(), decoded.getBalance());
        assertEquals(wallet.getCreatedAt(), decoded.getCreatedAt());
        assertEquals(wallet.getRank(), decoded.getRank());
    }

    @Test
    void codecRejectsEntityWithoutIdConstructor() {
        assertThrows(SchemaException.class, () -> new EntityCodec<>(Orphan.class));
    }

    @Test
    void namespaceBuildsKeys() {
        final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals("accounts:%s".formatted(id), RedisNamespace.of("accounts").getKey(id));
        assertEquals("accounts:lobby-1:%s".formatted(id), RedisNamespace.of("accounts").withTenant("lobby-1").getKey(id));
    }

    @Test
    void redisStoresUpdatesAndIncrements() {
        final RedisDriver redisDriver = connect();
        final String table = "test_%s".formatted(UUID.randomUUID().toString().replace("-", ""));
        final RedisStorage<Wallet> redisStorage = new RedisStorage<>(redisDriver, RedisNamespace.of(table), Wallet.class, Duration.ofMinutes(1));

        try {
            final Wallet wallet = this.createWallet("trae", 10L);
            redisStorage.put(wallet.getId(), wallet);
            assertEquals("trae", redisStorage.get(wallet.getId()).orElseThrow().getOwner());
            assertTrue(redisDriver.getCommands().pttl(redisStorage.getRedisNamespace().getKey(wallet.getId())) > 0);

            wallet.setOwner(null);
            redisStorage.putProperties(wallet, List.of(OWNER));
            assertFalse(redisDriver.getCommands().hgetall(redisStorage.getRedisNamespace().getKey(wallet.getId())).containsKey(OWNER.getName()));

            assertEquals(15L, redisStorage.increment(wallet.getId(), COINS, 5));
            assertThrows(SchemaException.class, () -> redisStorage.increment(wallet.getId(), BALANCE, 1));

            assertFalse(redisStorage.forTenant("lobby-2").contains(wallet.getId()));

            final UUID corruptId = UUID.randomUUID();
            redisDriver.getCommands().hset(redisStorage.getRedisNamespace().getKey(corruptId), COINS.getName(), "not-a-number");
            assertTrue(redisStorage.get(corruptId).isEmpty());
            assertFalse(redisStorage.contains(corruptId));
        } finally {
            final List<String> keyList = redisDriver.getCommands().keys("%s*".formatted(table));
            if (!keyList.isEmpty()) {
                redisDriver.getCommands().del(keyList.toArray(String[]::new));
            }

            redisDriver.disconnect();
        }
    }

    @Test
    void redisDeliversPublishedMessages() throws Exception {
        final RedisDriver redisDriver = connect();
        final CompletableFuture<String> received = new CompletableFuture<>();

        try {
            redisDriver.subscribe("test-channel", received::complete);
            redisDriver.publish("test-channel", "hello");

            assertEquals("hello", received.get(2, TimeUnit.SECONDS));
        } finally {
            redisDriver.disconnect();
        }
    }

    private static RedisDriver connect() {
        final RedisDriver redisDriver = new RedisDriver(new RedisSettings(System.getProperty("redis.host", "localhost"), Integer.getInteger("redis.port", 6379), System.getProperty("redis.password"), Integer.getInteger("redis.database", 15), Duration.ofSeconds(2)));

        try {
            redisDriver.connect();
        } catch (final ConnectionException exception) {
            Assumptions.abort("Redis is not reachable: %s".formatted(exception.getMessage()));
        }

        return redisDriver;
    }

    private Wallet createWallet(final String owner, final Long coins) {
        final Wallet wallet = new Wallet(UUID.randomUUID());
        wallet.setOwner(owner);
        wallet.setCoins(coins);
        return wallet;
    }

    public enum Rank {
        MEMBER, ADMIN
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class Wallet implements Entity {

        private final UUID id;
        private String owner;
        private Long coins;
        private BigDecimal balance;
        private Instant createdAt;
        private Rank rank;
    }

    private static final class Orphan implements Entity {

        @Override
        public UUID getId() {
            return null;
        }
    }
}
