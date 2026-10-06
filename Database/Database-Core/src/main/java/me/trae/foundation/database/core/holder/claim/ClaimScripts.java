package me.trae.foundation.database.core.holder.claim;

import io.lettuce.core.ScriptOutputType;
import lombok.experimental.UtilityClass;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.script.RedisScript;

import java.time.Duration;

@UtilityClass
public final class ClaimScripts {

    private final RedisScript CLAIM_SCRIPT = new RedisScript("if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then return ARGV[1] end return redis.call('GET', KEYS[1])");
    private final RedisScript STEAL_SCRIPT = new RedisScript("if redis.call('GET', KEYS[1]) == ARGV[1] and (ARGV[4] == '0' or redis.call('PTTL', KEYS[1]) == -1) then redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3]) return 1 end return 0");
    private final RedisScript PERSIST_SCRIPT = new RedisScript("if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('PERSIST', KEYS[1]) end return 0");
    private final RedisScript RELEASE_SCRIPT = new RedisScript("if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0");

    public String claim(final RedisDriver redisDriver, final String key, final String id, final Duration lease) {
        return CLAIM_SCRIPT.execute(redisDriver, ScriptOutputType.VALUE, new String[]{key}, id, String.valueOf(lease.toMillis()));
    }

    public boolean steal(final RedisDriver redisDriver, final String key, final String expectedOwner, final String id, final Duration lease, final boolean requirePersisted) {
        final Long result = STEAL_SCRIPT.execute(
                redisDriver,
                ScriptOutputType.INTEGER,
                new String[]{key},
                expectedOwner,
                id,
                String.valueOf(lease.toMillis()),
                requirePersisted ? "1" : "0"
        );

        return result != null && result == 1L;
    }

    public void persist(final RedisDriver redisDriver, final String key, final String id) {
        PERSIST_SCRIPT.execute(redisDriver, ScriptOutputType.INTEGER, new String[]{key}, id);
    }

    public void release(final RedisDriver redisDriver, final String key, final String id) {
        RELEASE_SCRIPT.execute(redisDriver, ScriptOutputType.INTEGER, new String[]{key}, id);
    }
}