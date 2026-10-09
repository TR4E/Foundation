package me.trae.foundation.database.api.query;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EntityPageTest {

    @Test
    void holdsWhatItWasGiven() {
        final List<Member> memberList = members(3);
        final EntityPage<Member> entityPage = new EntityPage<>(memberList, 3L, 1, 10);

        assertEquals(memberList, entityPage.getEntities());
        assertEquals(3L, entityPage.getTotalCount());
        assertEquals(1, entityPage.getPage());
        assertEquals(10, entityPage.getSize());
    }

    @Test
    void anEmptyPageHasOneTotalPage() {
        assertEquals(1, new EntityPage<>(List.<Member>of(), 0L, 1, 10).totalPages());
    }

    @Test
    void aPartialPageRoundsUp() {
        assertEquals(1, new EntityPage<>(members(1), 1L, 1, 10).totalPages());
        assertEquals(2, new EntityPage<>(members(10), 11L, 1, 10).totalPages());
        assertEquals(3, new EntityPage<>(members(10), 21L, 1, 10).totalPages());
    }

    @Test
    void anExactMultipleDoesNotGainAnEmptyPage() {
        assertEquals(1, new EntityPage<>(members(10), 10L, 1, 10).totalPages());
        assertEquals(2, new EntityPage<>(members(10), 20L, 1, 10).totalPages());
    }

    @Test
    void aSizeOfOneGivesAPagePerRow() {
        assertEquals(7, new EntityPage<>(members(1), 7L, 1, 1).totalPages());
    }

    @Test
    void aLargeCountDoesNotOverflow() {
        assertTrue(new EntityPage<>(members(10), 5_000_000_000L, 1, 10).totalPages() > 0);
    }

    private static List<Member> members(final int count) {
        return IntStream.range(0, count).mapToObj(_ -> new Member(UUID.randomUUID())).map(Member.class::cast).toList();
    }

    @RequiredArgsConstructor
    @Getter
    private static final class Member implements Entity {

        private final UUID id;
    }
}