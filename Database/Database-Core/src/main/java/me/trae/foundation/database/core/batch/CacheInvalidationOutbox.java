package me.trae.foundation.database.core.batch;

import lombok.RequiredArgsConstructor;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.RedisInvalidation;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public final class CacheInvalidationOutbox {

    private static final String DISPATCH_LOCK = "foundation:cache-invalidation-outbox:dispatcher";

    private static final Table<Record> TABLE = DSL.table(DSL.name("foundation_cache_invalidation_outbox"));
    private static final Field<UUID> ID = DSL.field(DSL.name("id"), SQLDataType.UUID.nullable(false));
    private static final Field<String> CACHE_KEY = DSL.field(DSL.name("cache_key"), SQLDataType.VARCHAR(1024).nullable(false));
    private static final Field<String> MISSING_KEY = DSL.field(DSL.name("missing_key"), SQLDataType.VARCHAR(1024).nullable(false));
    private static final Field<String> LOCK_KEY = DSL.field(DSL.name("lock_key"), SQLDataType.VARCHAR(1024).nullable(false));
    private static final Field<String> CHANNEL = DSL.field(DSL.name("channel"), SQLDataType.VARCHAR(1024).nullable(false));
    private static final Field<UUID> ENTITY_ID = DSL.field(DSL.name("entity_id"), SQLDataType.UUID.nullable(false));
    private static final Field<String> INSTANCE_ID = DSL.field(DSL.name("instance_id"), SQLDataType.VARCHAR(64).nullable(false));
    private static final Field<Boolean> INVALIDATE_CACHE = DSL.field(DSL.name("invalidate_cache"), SQLDataType.BOOLEAN.nullable(false).defaultValue(DSL.inline(true)));
    private static final Field<Instant> CREATED_AT = DSL.field(DSL.name("created_at"), SQLDataType.INSTANT.nullable(false));

    private static final int BATCH_SIZE = 25;
    private static final long POLL_INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();

    private final PostgresDriver postgresDriver;
    private final RedisDriver redisDriver;
    private volatile long lastPollNanos;

    public void synchronize() {
        this.postgresDriver.getDslContext().createTableIfNotExists(TABLE)
                .columns(ID, CACHE_KEY, MISSING_KEY, LOCK_KEY, CHANNEL, ENTITY_ID, INSTANCE_ID, INVALIDATE_CACHE, CREATED_AT)
                .constraint(DSL.primaryKey(ID))
                .execute();

        this.postgresDriver.getDslContext().alterTable(TABLE).addColumnIfNotExists(INVALIDATE_CACHE).execute();

        this.postgresDriver.getDslContext().createIndexIfNotExists(DSL.name("foundation_cache_outbox_created_idx"))
                .on(TABLE, CREATED_AT)
                .execute();
    }

    public static void insert(final DSLContext dslContext, final List<PendingWrite> writeList) {
        insertInvalidations(dslContext, writeList.stream().map(PendingWrite::getInvalidation).toList());
    }

    public static void insertInvalidations(final DSLContext dslContext, final List<RedisInvalidation> invalidationList) {
        final List<org.jooq.Query> queryList = new ArrayList<>();

        for (final RedisInvalidation invalidation : invalidationList) {
            if (invalidation == null) {
                continue;
            }

            queryList.add(dslContext.insertInto(TABLE)
                    .set(ID, UUID.randomUUID())
                    .set(CACHE_KEY, invalidation.getCacheKey())
                    .set(MISSING_KEY, invalidation.getMissingKey())
                    .set(LOCK_KEY, invalidation.getLockKey())
                    .set(CHANNEL, invalidation.getChannel())
                    .set(ENTITY_ID, invalidation.getEntityId())
                    .set(INSTANCE_ID, invalidation.getInstanceId())
                    .set(INVALIDATE_CACHE, invalidation.isInvalidateCache())
                    .set(CREATED_AT, Instant.now()));
        }

        if (!queryList.isEmpty()) {
            dslContext.batch(queryList).execute();
        }
    }

    public void dispatch() {
        this.dispatch(false);
    }

    public void dispatchNow() {
        this.dispatch(true);
    }

    private void dispatch(final boolean force) {
        if (this.redisDriver == null) {
            return;
        }

        final long currentTime = System.nanoTime();
        if (!force && currentTime - this.lastPollNanos < POLL_INTERVAL_NANOS) {
            return;
        }

        this.lastPollNanos = currentTime;
        final DSLContext dslContext = this.postgresDriver.getDslContext();

        if (!dslContext.fetchExists(TABLE)) {
            return;
        }

        final String owner = UUID.randomUUID().toString();
        final String dispatchLock = "%s:%s".formatted(DISPATCH_LOCK, this.postgresDriver.getPostgresSettings().getJdbcUrl());

        if (!this.redisDriver.tryAcquireLock(dispatchLock, owner, Duration.ofMinutes(2))) {
            return;
        }

        try {
            final org.jooq.Result<? extends Record> recordList = dslContext.select(ID, CACHE_KEY, MISSING_KEY, LOCK_KEY, CHANNEL, ENTITY_ID, INSTANCE_ID, INVALIDATE_CACHE)
                    .from(TABLE)
                    .orderBy(CREATED_AT)
                    .limit(BATCH_SIZE)
                    .fetch();

            for (final Record record : recordList) {
                new RedisInvalidation(
                        record.get(CACHE_KEY),
                        record.get(MISSING_KEY),
                        record.get(LOCK_KEY),
                        record.get(CHANNEL),
                        record.get(ENTITY_ID),
                        record.get(INSTANCE_ID),
                        record.get(INVALIDATE_CACHE)
                ).dispatch(this.redisDriver);
            }

            if (!recordList.isEmpty()) {
                dslContext.deleteFrom(TABLE).where(ID.in(recordList.getValues(ID))).execute();
            }
        } finally {
            this.redisDriver.releaseLock(dispatchLock, owner);
        }
    }
}