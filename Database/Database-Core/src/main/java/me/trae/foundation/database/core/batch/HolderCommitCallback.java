package me.trae.foundation.database.core.batch;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.util.function.Consumer;

@AllArgsConstructor
@Getter
public final class HolderCommitCallback implements WriteCommitCallback {

    private final Duration synchronizationTimeout;
    private final Consumer<PendingWrite> completionConsumer;

    @Override
    public void run(final PendingWrite pendingWrite) {
        this.completionConsumer.accept(pendingWrite);
    }
}