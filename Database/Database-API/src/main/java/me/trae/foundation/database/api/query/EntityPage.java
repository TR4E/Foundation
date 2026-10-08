package me.trae.foundation.database.api.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;

import java.util.List;

@AllArgsConstructor
@Getter
public final class EntityPage<E extends Entity> {

    private final List<E> entities;
    private final long totalCount;
    private final int page, size;

    public int totalPages() {
        return Math.max(1, (int) Math.ceil((double) this.totalCount / (double) this.size));
    }
}