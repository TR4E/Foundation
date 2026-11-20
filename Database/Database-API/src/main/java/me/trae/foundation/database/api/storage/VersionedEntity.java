package me.trae.foundation.database.api.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;

@AllArgsConstructor
@Getter
public final class VersionedEntity<EntityType extends Entity> {

    private final EntityType entity;
    private final long version;
}