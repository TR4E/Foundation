package me.trae.foundation.database.verification;

import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.change.ChangeType;
import me.trae.foundation.database.batch.BatchQueue;
import me.trae.foundation.database.batch.ChangeMerge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static me.trae.foundation.database.verification.DatabaseFixture.increment;
import static me.trae.foundation.database.verification.DatabaseFixture.set;
import static me.trae.foundation.database.verification.DatabaseFixture.write;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class BatchMatrixTest {

    @ParameterizedTest
    @CsvSource({
            "SAVE,SAVE,SAVE", "SAVE,UPDATE,SAVE", "SAVE,DELETE,DELETE",
            "UPDATE,SAVE,SAVE", "UPDATE,UPDATE,UPDATE", "UPDATE,DELETE,DELETE",
            "DELETE,SAVE,SAVE", "DELETE,UPDATE,DELETE", "DELETE,DELETE,DELETE"
    })
    void orderedPairMatrix(final WriteKind firstKind, final WriteKind secondKind, final WriteKind expectedKind) {
        final BatchQueue batchQueue = new BatchQueue(0);
        final UUID id = UUID.randomUUID();

        batchQueue.add(write(id, firstKind, 0L, set("counter", 1L)));
        batchQueue.add(write(id, secondKind, 1L, set("counter", 2L)));

        final var mergedWrite = batchQueue.drain().getFirst();

        assertEquals(expectedKind, mergedWrite.getKind(), "%s followed by %s".formatted(firstKind, secondKind));
        assertEquals(0, batchQueue.size());

        if (expectedKind != WriteKind.DELETE) {
            assertEquals(2L, mergedWrite.getChanges().getFirst().getValue());
        }
    }

    @Test
    void setOverIncrement() {
        final var change = ChangeMerge.merge(List.of(increment(3L)), List.of(set("counter", 7L))).getFirst();

        assertEquals(ChangeType.SET, change.getType());
        assertEquals(7L, change.getValue());
    }

    @Test
    void incrementOverSet() {
        final var change = ChangeMerge.merge(List.of(set("counter", 7L)), List.of(increment(3L))).getFirst();

        assertEquals(ChangeType.SET, change.getType());
        assertEquals(10L, change.getValue());
    }

    @Test
    void repeatedIncrementsSum() {
        final var changeList = ChangeMerge.merge(List.of(increment(2L)), List.of(increment(3L)));

        assertEquals(9L, ChangeMerge.merge(changeList, List.of(increment(4L))).getFirst().getValue());
    }
}