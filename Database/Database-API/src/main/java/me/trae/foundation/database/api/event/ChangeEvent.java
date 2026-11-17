package me.trae.foundation.database.api.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.change.Change;

import java.util.List;

@AllArgsConstructor
@Getter
public final class ChangeEvent {

    private final EntityKey key;
    private final long version;
    private final WriteKind kind;
    private final List<Change> changes;
}