package me.trae.foundation.utilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UtilArgonTest {

    @Test
    void hashesPasswordsVerifiesThemAndSupportsCacheInvalidation() {
        final int originalIterations = UtilArgon.getIterations();
        final int originalParallelism = UtilArgon.getParallelism();
        final int originalMemoryKb = UtilArgon.getMemoryKb();

        try {
            UtilArgon.setIterations(1);
            UtilArgon.setParallelism(1);
            UtilArgon.setMemoryKb(8);

            final String hash = UtilArgon.hash("correct horse", "pepper", "salt");

            assertNotNull(hash);
            assertTrue(UtilArgon.verify("correct horse", "pepper", "salt", hash));
            assertFalse(UtilArgon.verify("wrong horse", "pepper", "salt", hash));
            assertFalse(UtilArgon.verify("correct horse", "different pepper", "salt", hash));
            assertFalse(UtilArgon.verify("correct horse", "pepper", "different salt", hash));
            assertNull(UtilArgon.tryReHash("correct horse", "pepper", "salt", hash));

            UtilArgon.invalidateVerifyCache(hash);
            assertTrue(UtilArgon.verify("correct horse", "pepper", "salt", hash));
        } finally {
            UtilArgon.setIterations(originalIterations);
            UtilArgon.setParallelism(originalParallelism);
            UtilArgon.setMemoryKb(originalMemoryKb);
        }
    }

    @Test
    void requiresPositiveConcurrency() {
        assertThrows(IllegalArgumentException.class, () -> UtilArgon.setMaximumConcurrency(0));
        assertThrows(IllegalArgumentException.class, () -> UtilArgon.setMaximumConcurrency(-1));
    }
}