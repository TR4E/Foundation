package me.trae.foundation.database.core;

import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.core.holder.AbstractEntityHolder;

import java.time.Duration;

public final class WorkerHolder extends AbstractEntityHolder<WorkerEntity> {

    public WorkerHolder(final WorkerRepository repository) {
        super(repository, InstanceMode.MULTI_INSTANCE, Duration.ofMinutes(5), Duration.ofMinutes(5));
    }
}
