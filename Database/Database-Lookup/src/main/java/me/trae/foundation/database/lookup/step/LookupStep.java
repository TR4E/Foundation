package me.trae.foundation.database.lookup.step;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.holder.LookupTier;
import me.trae.foundation.database.api.storage.Storage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class LookupStep<Key, Value> {

    @Getter
    private final LookupTier lookupTier;

    private final Function<Key, Optional<Value>> reader;
    private final Function<Collection<Key>, Map<Key, Value>> bulkReader;
    private final BiConsumer<Key, Value> writer;

    public static <Key, Value> LookupStep<Key, Value> of(final LookupTier lookupTier, final Function<Key, Optional<Value>> reader, final Function<Collection<Key>, Map<Key, Value>> bulkReader, final BiConsumer<Key, Value> writer) {
        return new LookupStep<>(lookupTier, reader, bulkReader, writer);
    }

    public static <Key, Value> LookupStep<Key, Value> of(final LookupTier lookupTier, final Function<Key, Optional<Value>> reader, final Function<Collection<Key>, Map<Key, Value>> bulkReader) {
        return of(lookupTier, reader, bulkReader, null);
    }

    public static <Key, Value> LookupStep<Key, Value> of(final LookupTier lookupTier, final Function<Key, Optional<Value>> reader, final BiConsumer<Key, Value> writer) {
        return of(lookupTier, reader, null, writer);
    }

    public static <Key, Value> LookupStep<Key, Value> of(final LookupTier lookupTier, final Function<Key, Optional<Value>> reader) {
        return of(lookupTier, reader, null, null);
    }

    public static <Key, Value> LookupStep<Key, Value> of(final LookupTier lookupTier, final Storage<Key, Value> storage) {
        return of(lookupTier, storage::get, storage::getAll, storage::put);
    }

    public Optional<Value> read(final Key key) {
        return this.reader.apply(key);
    }

    public Map<Key, Value> readAll(final Collection<Key> keys) {
        if (this.bulkReader != null) {
            return this.bulkReader.apply(keys);
        }

        final Map<Key, Value> valueMap = new LinkedHashMap<>();

        for (final Key key : keys) {
            this.read(key).ifPresent(value -> valueMap.put(key, value));
        }

        return valueMap;
    }

    public void write(final Key key, final Value value) {
        if (this.writer != null) {
            this.writer.accept(key, value);
        }
    }
}