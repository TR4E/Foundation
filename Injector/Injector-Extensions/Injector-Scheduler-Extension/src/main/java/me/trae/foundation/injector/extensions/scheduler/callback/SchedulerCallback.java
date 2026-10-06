package me.trae.foundation.injector.extensions.scheduler.callback;

import java.util.concurrent.Executor;

public interface SchedulerCallback {

    default Executor getSynchronousExecutor() {
        return Runnable::run;
    }

    default Executor getAsynchronousExecutor() {
        return runnable -> Thread.ofVirtual().start(runnable);
    }
}