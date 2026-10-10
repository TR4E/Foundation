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
    private static final Field<Instant> CREATED_AT = DSL.field(DSL.name("created_at"), SQLDataType.INSTANT.nullable(false));

    private static final int BATCH_SIZE = 25;
    private static final long POLL_INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();

    private final PostgresDriver postgresDriver;
    private final RedisDriver redisDriver;
    private volatile long lastPollNanos;

    public void synchronize() {
        this.postgresDriver.getDslContext().createTableIfNotExists(TABLE)
                .columns(ID, CACHE_KEY, MISSING_KEY, LOCK_KEY, CHANNEL, ENTITY_ID, INSTANCE_ID, CREATED_AT)
                .constraint(DSL.primaryKey(ID))
                .execute();

        this.postgresDriver.getDslContext().createIndexIfNotExists(DSL.name("foundation_cache_outbox_created_idx"))
                .on(TABLE, CREATED_AT)
                .execute();
    }

    public static void insert(final DSLContext dslContext, final List<PendingWrite> writeList) {
        final List<org.jooq.Query> queryList = new ArrayList<>();

        for (final PendingWrite pendingWrite : writeList) {
            final RedisInvalidation invalidation = pendingWrite.getInvalidation();
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
                    .set(CREATED_AT, Instant.now()));
        }

        if (!queryList.isEmpty()) {
            dslContext.batch(queryList).execute();
        }
    }

    public void dispatch() {
        if (this.redisDriver == null) {
            return;
        }

        final long currentTime = System.nanoTime();
        if (currentTime - this.lastPollNanos < POLL_INTERVAL_NANOS) {
            return;
        }

        this.lastPollNanos = currentTime;
        final String owner = UUID.randomUUID().toString();

        if (!this.redisDriver.tryAcquireLock(DISPATCH_LOCK, owner, Duration.ofMinutes(2))) {
            return;
        }

        try {
            final DSLContext dslContext = this.postgresDriver.getDslContext();

            final org.jooq.Result<? extends Record> recordList = dslContext.select(ID, CACHE_KEY, MISSING_KEY, LOCK_KEY, CHANNEL, ENTITY_ID, INSTANCE_ID)
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
                        record.get(INSTANCE_ID)
                ).dispatch(this.redisDriver);
            }

            if (!recordList.isEmpty()) {
                dslContext.deleteFrom(TABLE).where(ID.in(recordList.getValues(ID))).execute();
            }
        } finally {
            this.redisDriver.releaseLock(DISPATCH_LOCK, owner);
        }
    }
}