package me.trae.foundation.database.api.key;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@EqualsAndHashCode
@AllArgsConstructor
@Getter
public final class EntityKey {

    private final String table, tenant;
    private final UUID id;
}