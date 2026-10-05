package me.trae.foundation.injector.api.extension;

import me.trae.foundation.injector.api.callback.ApplicationCallback;

import java.util.Optional;

public interface Extension {

    default boolean isComponent(final Class<?> type) {
        return false;
    }

    default <T> Optional<T> instantiate(final Class<?> applicationClass, final ApplicationCallback applicationCallback, final Class<T> type) {
        return Optional.empty();
    }

    default void onApplicationInitialize(final Class<?> applicationClass, final ApplicationCallback applicationCallback) {
    }

    default void onApplicationShutdown(final Class<?> applicationClass, final ApplicationCallback applicationCallback) {
    }

    default void onComponentCreate(final Class<?> applicationClass, final ApplicationCallback applicationCallback, final Object component) {
    }

    default void onComponentShutdown(final Class<?> applicationClass, final ApplicationCallback applicationCallback, final Object component) {
    }
}