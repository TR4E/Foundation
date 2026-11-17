package me.trae.foundation.database.redis.event;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.event.ChangeEvent;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
public final class RedisEvents {

    public String encode(final List<Change> changes) {
        return changes.stream()
                .map(RedisEvents::line)
                .collect(Collectors.joining("\n"));
    }

    public ChangeEvent decode(final EntityKey key, final String message) {
        final String[] lines = message.split("\n");
        final List<Change> changes = Arrays.stream(lines, 3, lines.length)
                .map(RedisEvents::change)
                .collect(Collectors.toCollection(ArrayList::new));

        return new ChangeEvent(key, Long.parseLong(lines[0]), WriteKind.valueOf(lines[2]), changes);
    }

    public String origin(final String message) {
        return message.split("\n", 4)[1];
    }

    private String line(final Change change) {
        return change.getColumn() + "\t" + change.getType().name() + "\t" + encodeValue(change.getValue());
    }

    private Change change(final String line) {
        final String[] parts = line.split("\t", 3);

        return new Change(parts[0], ChangeType.valueOf(parts[1]), decodeValue(parts[2]));
    }

    private String encodeValue(final Object value) {
        return value == null ? "~" : Base64.getEncoder().encodeToString(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
    }

    private String decodeValue(final String raw) {
        return raw.equals("~") ? null : new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
    }
}