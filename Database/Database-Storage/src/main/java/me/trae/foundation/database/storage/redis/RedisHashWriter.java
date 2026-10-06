package me.trae.foundation.database.storage.redis;

import io.lettuce.core.ScriptOutputType;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.script.RedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RedisHashWriter {

    private static final RedisScript WRITE_SCRIPT = new RedisScript(
            """
                    local setCount = tonumber(ARGV[1])
                    for index = 0, setCount - 1 do
                        redis.call('HSET', KEYS[1], ARGV[2 + index * 2], ARGV[3 + index * 2])
                    end
                    local deleteStart = 2 + setCount * 2
                    local deleteCount = tonumber(ARGV[deleteStart])
                    for index = 1, deleteCount do
                        redis.call('HDEL', KEYS[1], ARGV[deleteStart + index])
                    end
                    local expiry = tonumber(ARGV[deleteStart + deleteCount + 1])
                    if expiry > 0 and redis.call('EXISTS', KEYS[1]) == 1 then
                        redis.call('PEXPIRE', KEYS[1], expiry)
                    end
                    return 1
                    """
    );

    private static final RedisScript INCREMENT_SCRIPT = new RedisScript(
            """
                    local value = redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2])
                    local expiry = tonumber(ARGV[3])
                    if expiry > 0 then
                        redis.call('PEXPIRE', KEYS[1], expiry)
                    end
                    return value
                    """
    );

    private final RedisDriver redisDriver;
    private final long expiryMillis;

    public RedisHashWriter(final RedisDriver redisDriver, final Duration expiry) {
        this.redisDriver = redisDriver;
        this.expiryMillis = expiry == null ? 0L : expiry.toMillis();
    }

    public void write(final String key, final Map<String, String> fieldMap) {
        final List<String> setList = new ArrayList<>();
        final List<String> deleteList = new ArrayList<>();

        fieldMap.forEach((field, value) -> {
            if (value == null) {
                deleteList.add(field);
                return;
            }

            setList.add(field);
            setList.add(value);
        });

        final List<String> argumentList = new ArrayList<>();

        argumentList.add(String.valueOf(setList.size() / 2));
        argumentList.addAll(setList);
        argumentList.add(String.valueOf(deleteList.size()));
        argumentList.addAll(deleteList);
        argumentList.add(String.valueOf(this.expiryMillis));

        WRITE_SCRIPT.execute(this.redisDriver, ScriptOutputType.INTEGER, new String[]{key}, argumentList.toArray(String[]::new));
    }

    public long increment(final String key, final String field, final long delta) {
        return INCREMENT_SCRIPT.<Long>execute(
                this.redisDriver,
                ScriptOutputType.INTEGER,
                new String[]{key},
                field,
                String.valueOf(delta),
                String.valueOf(this.expiryMillis)
        );
    }
}