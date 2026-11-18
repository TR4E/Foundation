package me.trae.foundation.database.lookup;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.converter.ValueConverter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.concurrency.Stampede;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@AllArgsConstructor
@Getter
public final class LoadGuard<EntityType extends Entity> {

    private final Stampede stampede;
    private final ValueConverter<EntityType, String> converter;
    private final Function<UUID, String> keyFactory;

    public Optional<EntityType> load(final UUID id, final Supplier<Optional<EntityType>> loader) {
        if (this.stampede == null) {
            return loader.get();
        }

        return this.stampede.load(this.keyFactory.apply(id), this.converter, loader);
    }
}