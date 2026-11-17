package me.trae.foundation.database.batch;

import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class BatchQueue {

    private final ConcurrentHashMap<EntityKey, Write> pendingMap = new ConcurrentHashMap<>();

    public void add(final Write write) {
        this.pendingMap.merge(write.getKey(), write, this::merge);
    }

    public List<Write> drain() {
        final List<Write> writes = new ArrayList<>(this.pendingMap.values());

        writes.forEach(write -> this.pendingMap.remove(write.getKey(), write));

        return writes;
    }

    public int size() {
        return this.pendingMap.size();
    }

    private Write merge(final Write existing, final Write incoming) {
        if (incoming.getKind() != WriteKind.UPDATE) {
            return incoming;
        }

        if (existing.getKind() == WriteKind.DELETE) {
            return existing;
        }

        return new Write(incoming.getKey(), existing.getKind(), ChangeMerge.merge(existing.getChanges(), incoming.getChanges()), incoming.getExpectedVersion());
    }
}