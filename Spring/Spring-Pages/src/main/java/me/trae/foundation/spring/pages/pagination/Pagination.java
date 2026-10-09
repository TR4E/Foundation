package me.trae.foundation.spring.pages.pagination;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public final class Pagination {

    private final int page, size;
    private final long totalCount;
    private final int totalPages, windowStart, windowEnd;

    public static Pagination of(final int page, final long totalCount, final int size, final int windowSize) {
        if (size < 1) {
            throw new IllegalArgumentException("Page size must be at least 1");
        }
        final int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / (double) size));
        final int current = Math.clamp(page, 1, totalPages);

        final int windowEnd = Math.min(totalPages, current + Math.max(1, windowSize) / 2);
        final int windowStart = Math.max(1, windowEnd - Math.max(1, windowSize) + 1);

        return new Pagination(current, size, totalCount, totalPages, windowStart, Math.min(totalPages, windowStart + Math.max(1, windowSize) - 1));
    }

    public int offset() {
        return (this.page - 1) * this.size;
    }

    public boolean hasPrevious() {
        return this.page > 1;
    }

    public boolean hasNext() {
        return this.page < this.totalPages;
    }

    public boolean isEmpty() {
        return this.totalCount == 0L;
    }
}