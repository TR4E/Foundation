package me.trae.foundation.database.redis.write;

import io.lettuce.core.ScriptOutputType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.exception.VersionConflictException;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.WriteResult;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;
import me.trae.foundation.database.api.write.change.Decrement;
import me.trae.foundation.database.redis.codec.ValueCodec;
import me.trae.foundation.database.redis.driver.RedisDriver;
import me.trae.foundation.database.redis.event.RedisEvents;
import me.trae.foundation.database.redis.script.RedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public final class RedisEntityWriter {

    private static final RedisScript WRITE = new RedisScript("""
            if redis.call('EXISTS', KEYS[1]) == 0 and ARGV[4] ~= 'SAVE' then return {0, ''} end
            local version = tonumber(redis.call('HGET', KEYS[1], 'version')) or 0
            local expected = tonumber(ARGV[1])
            if expected > 0 and version == 0 then
                redis.call('HSET', KEYS[1], 'version', expected)
                version = expected
            end
            if expected > 0 and version ~= expected then return {-1, ''} end
            local projected = {}
            local index = 6
            while index <= #ARGV do
                local column = ARGV[index]
                local operation = ARGV[index + 1]
                local value = ARGV[index + 2]
                if operation == 'SET' then
                    projected[column] = tonumber(value)
                elseif operation == 'UNSET' then
                    projected[column] = 0
                elseif operation == 'INCREMENT' then
                    local base = projected[column]
                    if base == nil then base = tonumber(redis.call('HGET', KEYS[1], column)) or 0 end
                    projected[column] = base + tonumber(value)
                elseif operation == 'DECREMENT_IF' then
                    local delta, floor = string.match(value, '(-?%d+):(-?%d+)')
                    local base = projected[column]
                    if base == nil then base = tonumber(redis.call('HGET', KEYS[1], column)) or 0 end
                    if base >= 9007199254740992 or base <= -9007199254740992 then return {-3, ''} end
                    if base - tonumber(delta) < tonumber(floor) then return {-2, ''} end
                    projected[column] = base - tonumber(delta)
                end
                index = index + 3
            end
            local counter = ''
            index = 6
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
                    counter = redis.call('HGET', KEYS[1], column)
                elseif operation == 'DECREMENT_IF' then
                    local delta = string.match(value, '(-?%d+):')
                    redis.call('HINCRBY', KEYS[1], column, -tonumber(delta))
                    counter = redis.call('HGET', KEYS[1], column)
                end
                index = index + 3
            end
            local updated = redis.call('HINCRBY', KEYS[1], 'version', 1)
            local ttl = tonumber(ARGV[2])
            if ttl > 0 then redis.call('PEXPIRE', KEYS[1], ttl) end
            redis.call('PUBLISH', KEYS[2], updated .. '\\n' .. ARGV[3] .. '\\n' .. ARGV[4] .. '\\n' .. ARGV[5])
            return {updated, counter}
            """);

    private static final RedisScript DELETE = new RedisScript("""
            redis.call('DEL', KEYS[1])
            redis.call('PUBLISH', KEYS[2], ARGV[1])
            return 1
            """);

    private final RedisDriver driver;
    private final List<Property<?, ?>> properties;

    public WriteResult apply(final String path, final String channel, final Write write, final Duration ttl) {
        final List<Change> changes = write.getChanges().stream().map(this::encoded).collect(Collectors.toCollection(ArrayList::new));

        final List<Change> published = changes.stream().map(RedisEntityWriter::published).collect(Collectors.toCollection(ArrayList::new));

        final List<String> arguments = new ArrayList<>();

        arguments.add(String.valueOf(write.getExpectedVersion()));
        arguments.add(String.valueOf(ttl.toMillis()));
        arguments.add(this.driver.getInstanceId());
        arguments.add(write.getKind().name());
        arguments.add(RedisEvents.encode(published));

        changes.forEach(change -> this.argument(arguments, change));

        final List<Object> result = WRITE.execute(this.driver, ScriptOutputType.MULTI, new String[]{path, channel}, arguments.toArray(String[]::new));

        final long version = Long.class.cast(result.getFirst());

        if (version == -1) {
            throw new VersionConflictException("Write to %s expected version %s".formatted(path, write.getExpectedVersion()));
        }

        return new WriteResult(version, counter(result));
    }

    public void delete(final String path, final String channel, final long version) {
        DELETE.execute(this.driver, ScriptOutputType.INTEGER, new String[]{path, channel}, version + "\n" + this.driver.getInstanceId() + "\n" + WriteKind.DELETE.name() + "\n");
    }

    private Change encoded(final Change change) {
        if (change.getType() == ChangeType.INCREMENT || change.getType() == ChangeType.DECREMENT_IF) {
            return change;
        }

        return new Change(change.getColumn(), change.getType(), this.value(change));
    }

    private void argument(final List<String> arguments, final Change change) {
        arguments.add(change.getColumn());

        arguments.add(this.operation(change));

        arguments.add(this.argument(change));
    }

    private String argument(final Change change) {
        if (change.getValue() instanceof final Decrement decrement) {
            return decrement.getDelta() + ":" + decrement.getFloor();
        }

        return change.getValue() == null ? "" : String.valueOf(change.getValue());
    }

    private String operation(final Change change) {
        if (change.getType() == ChangeType.DECREMENT_IF) {
            return "DECREMENT_IF";
        }

        if (change.getType() == ChangeType.INCREMENT) {
            return "INCREMENT";
        }

        return change.getValue() == null ? "UNSET" : "SET";
    }

    private String value(final Change change) {
        if (change.getValue() == null) {
            return null;
        }

        return this.properties.stream()
                .filter(property -> property.getColumn().equals(change.getColumn()))
                .findFirst()
                .map(property -> encode(property, change.getValue()))
                .orElseGet(() -> String.valueOf(change.getValue()));
    }

    private static Long counter(final List<Object> result) {
        final String counter = String.valueOf(result.get(1));

        return counter.isEmpty() ? null : Long.valueOf(counter);
    }

    private static Change published(final Change change) {
        if (change.getValue() instanceof final Decrement decrement) {
            return new Change(change.getColumn(), ChangeType.INCREMENT, -decrement.getDelta());
        }

        return change;
    }

    private static <Owner, Value> String encode(final Property<Owner, Value> property, final Object value) {
        return ValueCodec.encode(property, property.getType().cast(value));
    }
}