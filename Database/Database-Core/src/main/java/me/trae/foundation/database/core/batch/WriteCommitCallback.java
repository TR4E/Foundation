package me.trae.foundation.database.core.batch;

import java.time.Duration;

/**
 * Provides the coalesced committed write to holder synchronization callbacks.
 */
@FunctionalInterface
public interface WriteCommitCallback extends Runnable {

    void run(final PendingWrite pendingWrite);

    default Duration getSynchronizationTimeout() {
        return null;
    }

    @Override
    default void run() {
        this.run(null);
    }
}