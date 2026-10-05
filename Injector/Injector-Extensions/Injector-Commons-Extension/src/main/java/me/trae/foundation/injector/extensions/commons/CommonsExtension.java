package me.trae.foundation.injector.extensions.commons;

import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.api.extension.Extension;
import me.trae.foundation.injector.extensions.commons.annotation.ApplicationReady;
import me.trae.foundation.injector.extensions.commons.annotation.PostConstruct;
import me.trae.foundation.injector.extensions.commons.annotation.PostDestroy;
import me.trae.foundation.injector.extensions.commons.annotation.PreDestroy;
import me.trae.foundation.injector.extensions.commons.resolver.MethodResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CommonsExtension implements Extension {

    private final Map<Class<?>, List<Object>> componentMap = new HashMap<>();

    @Override
    public void onComponentCreate(final Class<?> applicationClass, final ApplicationCallback applicationCallback, final Object component) {
        this.componentMap.computeIfAbsent(applicationClass, key -> new ArrayList<>()).add(component);

        MethodResolver.invoke(component, PostConstruct.class);
    }

    @Override
    public void onApplicationInitialize(final Class<?> applicationClass, final ApplicationCallback applicationCallback) {
        this.componentMap.getOrDefault(applicationClass, Collections.emptyList()).forEach(component -> {
            MethodResolver.invoke(component, ApplicationReady.class);
        });
    }

    @Override
    public void onComponentShutdown(final Class<?> applicationClass, final ApplicationCallback applicationCallback, final Object component) {
        MethodResolver.invoke(component, PreDestroy.class);
    }

    @Override
    public void onApplicationShutdown(final Class<?> applicationClass, final ApplicationCallback applicationCallback) {
        final List<Object> componentList = this.componentMap.remove(applicationClass);
        if (componentList == null) {
            return;
        }

        componentList.reversed().forEach(component -> {
            MethodResolver.invoke(component, PostDestroy.class);
        });
    }
}