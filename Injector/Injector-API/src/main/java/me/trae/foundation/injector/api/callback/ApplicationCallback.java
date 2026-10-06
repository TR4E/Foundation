package me.trae.foundation.injector.api.callback;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Executor;

public interface ApplicationCallback {

    default Executor getSynchronousExecutor() {
        return Runnable::run;
    }

    default Executor getAsynchronousExecutor() {
        return runnable -> Thread.ofVirtual().start(runnable);
    }

    default Path getDataFolder() {
        return Path.of("");
    }

    default Comparator<Class<?>> getComponentSorter() {
        return (_, _) -> 0;
    }

    default void onComponentRegister(final Object component) {
    }

    default void onComponentUnregister(final Object component) {
    }

    default void onApplicationFailure(final Throwable throwable) {
        Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), throwable);
    }
}