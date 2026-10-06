package me.trae.foundation.injector.api;

import me.trae.foundation.injector.api.exception.ImplementationNotFoundException;

import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

public interface Injector {

    Injector INSTANCE = ServiceLoader.load(Injector.class, Injector.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new ImplementationNotFoundException("No Injector implementation found on the classpath"));

    void initialize(final Object application, final List<Class<?>> componentClassList);

    default void initialize(final Object application) {
        this.initialize(application, Collections.emptyList());
    }

    void shutdown(final Object application);

    <T> T get(final Class<T> type);

    <T> List<T> getAll(final Class<T> type);

    List<Class<?>> getComponents(final Class<?> applicationClass);

    void attach(final Object application, final List<Class<?>> componentClassList);

    void detach(final Object application, final List<Class<?>> componentClassList);
}