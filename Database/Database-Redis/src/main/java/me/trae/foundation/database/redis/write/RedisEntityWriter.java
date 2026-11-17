package me.trae.foundation.database.redis.write;

import io.lettuce.core.ScriptOutputType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.exception.VersionConflictException;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;
import me.trae.foundation.database.redis.codec.ValueCodec;
import me.trae.foundation.database.redis.driver.RedisDriver;
import me.trae.foundation.database.redis.event.RedisEvents;
import me.trae.foundation.database.redis.script.RedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Getter
public final class RedisEntityWriter {

    private static final RedisScript WRITE = new RedisScript("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            local version = tonumber(redis.call('HGET', KEYS[1], 'version')) or 0
            local expected = tonumber(ARGV[1])
            if expected > 0 and version ~= expected then return -1 end
            local index = 6
            while index <= #ARGV do
                local column = ARGV[index]
                local operation = ARGV[index + 1]
                local value = ARGV[index + 2]
                if operation == 'SET' then
                    redis.call('HSET', KEYS[1], column, value)
                elseif operation == 'UNSET' then
                    redis.call('HDEL', KEYS[1], column)
                elseif operation == 'INCREMENT' then
                    redis.call('HINCRBY', KEYS[1], column, value)
                end
                index = index + 3
            end
            local updated = redis.call('HINCRBY', KEYS[1], 'version', 1)
            local ttl = tonumber(ARGV[2])
            if ttl > 0 then redis.call('PEXPIRE', KEYS[1], ttl) end
            redis.call('PUBLISH', KEYS[2], updated .. '\\n' .. ARGV[3] .. '\\n' .. ARGV[4] .. '\\n' .. ARGV[5])
            return updated
            """);

    private static final RedisScript DELETE = new RedisScript("""
            redis.call('DEL', KEYS[1])
            redis.call('PUBLISH', KEYS[2], ARGV[1])
            return 1
            """);

    private final RedisDriver driver;
    private final List<Property<?, ?>> properties;

    public long apply(final String path, final String channel, final Write write, final Duration ttl) {
        final List<String> arguments = new ArrayList<>();

        arguments.add(String.valueOf(write.getExpectedVersion()));
        arguments.add(String.valueOf(ttl.toMillis()));
        arguments.add(this.driver.getInstanceId());
        arguments.add(write.getKind().name());
        arguments.add(RedisEvents.encode(write.getChanges()));

        write.getChanges().forEach(change -> this.argument(arguments, change));

        final Long version = WRITE.execute(this.driver, ScriptOutputType.INTEGER, new String[]{path, channel}, arguments.toArray(String[]::new));

        if (version == -1) {
            throw new VersionConflictException("Write to %s expected version %s".formatted(path, write.getExpectedVersion()));
        }

        return version;
    }

    public void delete(final String path, final String channel, final long version) {
        DELETE.execute(this.driver, ScriptOutputType.INTEGER, new String[]{path, channel}, version + "\n" + this.driver.getInstanceId() + "\n" + WriteKind.DELETE.name() + "\n");
    }

    private void argument(final List<String> arguments, final Change change) {
        arguments.add(change.getColumn());
        arguments.add(this.operation(change));
        arguments.add(this.value(change));
    }

    private String operation(final Change change) {
        if (change.getType() == ChangeType.INCREMENT) {
            return "INCREMENT";
        }

        return change.getValue() == null ? "UNSET" : "SET";
    }

    private String value(final Change change) {
        return this.properties.stream()
                .filter(property -> property.getColumn().equals(change.getColumn()))
                .findFirst()
                .map(property -> encode(property, change.getValue()))
                .orElse(String.valueOf(change.getValue()));
    }

    private static <Owner, Value> String encode(final Property<Owner, Value> property, final Object value) {
        return value == null ? "" : ValueCodec.encode(property, property.getType().cast(value));
    }
}