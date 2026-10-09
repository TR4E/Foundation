package me.trae.foundation.spring.pages.pagination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PaginationTest {

    @Test
    void rejectsNonPositivePageSizes() {
        assertThrows(IllegalArgumentException.class, () -> Pagination.of(1, 10L, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> Pagination.of(1, 10L, -1, 5));
    }

    @Test
    void emptyResultsStillExposeTheFirstPage() {
        final Pagination pagination = Pagination.of(20, 0L, 10, 5);

        assertEquals(1, pagination.getPage());
        assertEquals(1, pagination.getTotalPages());
        assertEquals(1, pagination.getWindowStart());
        assertEquals(1, pagination.getWindowEnd());
        assertEquals(0, pagination.offset());
        assertTrue(pagination.isEmpty());
        assertFalse(pagination.hasPrevious());
        assertFalse(pagination.hasNext());
    }

    @Test
    void requestedPagesAreClampedIntoTheAvailableRange() {
        assertEquals(1, Pagination.of(-10, 95L, 10, 5).getPage());
        assertEquals(10, Pagination.of(100, 95L, 10, 5).getPage());
    }

    @Test
    void calculatesNavigationAndRepositoryOffset() {
        final Pagination pagination = Pagination.of(3, 95L, 10, 5);

        assertEquals(20, pagination.offset());
        assertEquals(10, pagination.getTotalPages());
        assertTrue(pagination.hasPrevious());
        assertTrue(pagination.hasNext());
        assertFalse(pagination.isEmpty());
    }

    @Test
    void windowStaysFullAtTheBeginningMiddleAndEnd() {
        final Pagination beginning = Pagination.of(1, 100L, 10, 5);
        final Pagination middle = Pagination.of(6, 100L, 10, 5);
        final Pagination end = Pagination.of(10, 100L, 10, 5);

        assertEquals(1, beginning.getWindowStart());
        assertEquals(5, beginning.getWindowEnd());
        assertEquals(4, middle.getWindowStart());
        assertEquals(8, middle.getWindowEnd());
        assertEquals(6, end.getWindowStart());
        assertEquals(10, end.getWindowEnd());
    }

    @Test
    void nonPositiveWindowSizesBehaveAsOne() {
        final Pagination pagination = Pagination.of(4, 100L, 10, 0);

        assertEquals(4, pagination.getWindowStart());
        assertEquals(4, pagination.getWindowEnd());
    }
}