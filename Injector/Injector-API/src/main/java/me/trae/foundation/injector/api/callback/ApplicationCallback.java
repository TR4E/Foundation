package me.trae.foundation.injector.api.callback;

import java.util.Comparator;

public interface ApplicationCallback {

    default Comparator<Class<?>> getComponentSorter() {
        return Comparator.comparing(Class::getName);
    }

    default void onComponentRegister(final Object component) {
    }

    default void onComponentUnregister(final Object component) {
    }

    default void onApplicationFailure(final Throwable throwable) {
        Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), throwable);
    }
}