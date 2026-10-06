package me.trae.foundation.injector.core;

import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.api.extension.Extension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingExtension implements Extension {

    private static final Map<Class<?>, List<String>> EVENT_MAP = new ConcurrentHashMap<>();

    public static List<String> getEvents(final Class<?> applicationClass) {
        return EVENT_MAP.getOrDefault(applicationClass, Collections.emptyList());
    }

    @Override
    public <T> Optional<T> instantiate(final Class<?> applicationClass, final ApplicationCallback applicationCallback, final Class<T> type) {
        if (type != InjectorTest.Made.class) {
            return Optional.empty();
        }

        return Optional.of(type.cast(new InjectorTest.Made("extension")));
    }

    @Override
    public void onApplicationInitialize(final Class<?> applicationClass, final ApplicationCallback applicationCallback) {
        this.record(applicationClass, "initialize");
    }

    @Override
    public void onApplicationShutdown(final Class<?> applicationClass, final ApplicationCallback applicationCallback) {
        this.record(applicationClass, "shutdown");
    }

    @Override
    public void onComponentCreate(final Class<?> applicationClass, final ApplicationCallback applicationCallback, final Object component) {
        this.record(applicationClass, "create:%s".formatted(component.getClass().getSimpleName()));
    }

    @Override
    public void onComponentShutdown(final Class<?> applicationClass, final ApplicationCallback applicationCallback, final Object component) {
        this.record(applicationClass, "shutdown:%s".formatted(component.getClass().getSimpleName()));
    }

    private void record(final Class<?> applicationClass, final String event) {
        EVENT_MAP.computeIfAbsent(applicationClass, _ -> new CopyOnWriteArrayList<>()).add(event);
    }
}
