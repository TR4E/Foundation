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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
@Getter
public final class RedisEntityWriter {

    private static final RedisScript WRITE = new RedisScript("""
            local kind = ARGV[4]
            local expected = tonumber(ARGV[1])
            local guarded = false
            local commutative = true
            local index = 6
            while index <= #ARGV do
                local operation = ARGV[index + 1]
                if operation == 'DECREMENT_IF' then guarded = true end
                if operation ~= 'INCREMENT' and operation ~= 'DECREMENT_IF' then commutative = false end
                index = index + 3
            end
            local raw = redis.call('HGET', KEYS[1], 'version')
            local version = tonumber(raw) or 0
            if not raw and kind ~= 'SAVE' and redis.call('HLEN', KEYS[1]) == 0 then return {0, ''} end
            if expected > 0 and version == 0 then
                redis.call('HSET', KEYS[1], 'version', expected)
                version = expected
            end
            if expected > 0 and version ~= expected and not commutative then return {-1, ''} end
            if guarded then
                local projected = {}
                index = 6
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
                    counter = redis.call('HINCRBY', KEYS[1], column, value)
                    if counter >= 9007199254740992 or counter <= -9007199254740992 then counter = redis.call('HGET', KEYS[1], column) end
                elseif operation == 'DECREMENT_IF' then
                    counter = redis.call('HINCRBY', KEYS[1], column, -tonumber(string.match(value, '(-?%d+):')))
                    if counter >= 9007199254740992 or counter <= -9007199254740992 then counter = redis.call('HGET', KEYS[1], column) end
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

    private final ConcurrentHashMap<String, Property<?, ?>> columnMap = new ConcurrentHashMap<>();

    private final RedisDriver driver;
    private final List<Property<?, ?>> properties;

    public WriteResult apply(final String path, final String channel, final Write write, final Duration ttl) {
        final List<Change> changes = write.getChanges();

        final String[] arguments = new String[5 + changes.size() * 3];

        final StringBuilder payload = new StringBuilder();

        arguments[0] = String.valueOf(write.getExpectedVersion());
        arguments[1] = String.valueOf(ttl.toMillis());
        arguments[2] = this.driver.getInstanceId();
        arguments[3] = write.getKind().name();

        for (int index = 0; index < changes.size(); index++) {
            this.append(arguments, payload, changes.get(index), 5 + index * 3);
        }

        arguments[4] = payload.toString();

        final List<Object> result = WRITE.execute(this.driver, ScriptOutputType.MULTI, new String[]{path, channel}, arguments);

        final long version = Long.class.cast(result.getFirst());

        if (version == -1) {
            throw new VersionConflictException("Write to %s expected version %s".formatted(path, write.getExpectedVersion()));
        }

        return new WriteResult(version, counter(result));
    }

    public void delete(final String path, final String channel, final long version) {
        DELETE.execute(this.driver, ScriptOutputType.INTEGER, new String[]{path, channel}, version + "\n" + this.driver.getInstanceId() + "\n" + WriteKind.DELETE.name() + "\n");
    }

    private void append(final String[] arguments, final StringBuilder payload, final Change change, final int offset) {
        final String value = this.encoded(change);

        arguments[offset] = change.getColumn();
        arguments[offset + 1] = this.operation(change);
        arguments[offset + 2] = value == null ? "" : value;

        if (!payload.isEmpty()) {
            payload.append('\n');
        }

        publish(payload, change, value);
    }

    private String encoded(final Change change) {
        if (change.getValue() instanceof final Decrement decrement) {
            return decrement.getDelta() + ":" + decrement.getFloor();
        }

        if (change.getType() == ChangeType.INCREMENT) {
            return String.valueOf(change.getValue());
        }

        return this.value(change);
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

        final Property<?, ?> property = this.property(change.getColumn());

        return property == null ? String.valueOf(change.getValue()) : encode(property, change.getValue());
    }

    private Property<?, ?> property(final String column) {
        return this.columnMap.computeIfAbsent(column, name -> this.properties.stream()
                .filter(property -> property.getColumn().equals(name))
                .findFirst()
                .orElse(null));
    }

    private static Long counter(final List<Object> result) {
        final String counter = String.valueOf(result.get(1));

        return counter.isEmpty() ? null : Long.valueOf(counter);
    }

    private static void publish(final StringBuilder payload, final Change change, final String value) {
        payload.append(change.getColumn()).append('\t');

        if (change.getValue() instanceof final Decrement decrement) {
            payload.append(ChangeType.INCREMENT.name()).append('\t').append(RedisEvents.encodeValue(String.valueOf(-decrement.getDelta())));
            return;
        }

        payload.append(change.getType().name()).append('\t').append(RedisEvents.encodeValue(value));
    }

    private static <Owner, Value> String encode(final Property<Owner, Value> property, final Object value) {
        return ValueCodec.encode(property, property.getType().cast(value));
    }
}