package me.trae.foundation.database.api.write;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.write.change.Change;

import java.util.List;

@AllArgsConstructor
@Getter
public final class Write {

    private final EntityKey key;
    private final WriteKind kind;
    private final List<Change> changes;
    private final long expectedVersion;
}