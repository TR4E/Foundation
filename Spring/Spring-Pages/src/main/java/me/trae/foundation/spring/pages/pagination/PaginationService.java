package me.trae.foundation.spring.pages.pagination;

import lombok.AllArgsConstructor;
import me.trae.foundation.spring.pages.PageProperties;

@AllArgsConstructor
public final class PaginationService {

    private final PageProperties pageProperties;

    public Pagination getPagination(final int page, final long totalCount, final int size) {
        return Pagination.of(page, totalCount, size, this.pageProperties.getPaginationWindowSize());
    }

    public Pagination getPagination(final int page, final long totalCount) {
        return this.getPagination(page, totalCount, this.pageProperties.getPageSize());
    }

    public int getPageSize() {
        return this.pageProperties.getPageSize();
    }
}