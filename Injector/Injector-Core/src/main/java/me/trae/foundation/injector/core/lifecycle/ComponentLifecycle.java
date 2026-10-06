package me.trae.foundation.injector.core.lifecycle;

import lombok.AllArgsConstructor;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.injector.core.application.ApplicationContext;
import me.trae.foundation.injector.core.container.ComponentContainer;
import me.trae.foundation.injector.core.extension.ExtensionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
public final class ComponentLifecycle {

    private final ComponentContainer componentContainer;
    private final ExtensionRegistry extensionRegistry;

    public void initialize(final ApplicationContext applicationContext) {
        for (final Class<?> type : applicationContext.getComponentClassList()) {
            this.componentContainer.getInstance(type).ifPresent(instance -> this.initializeComponent(applicationContext, instance));
        }

        this.extensionRegistry.onApplicationInitialize(applicationContext);
    }

    public void shutdown(final ApplicationContext applicationContext) {
        for (final Class<?> type : applicationContext.getComponentClassList().reversed()) {
            this.componentContainer.getInstance(type).ifPresent(instance -> this.shutdownComponent(applicationContext, instance));

            this.componentContainer.unregister(type);
        }

        this.extensionRegistry.onApplicationShutdown(applicationContext);
    }

    public void attach(final ApplicationContext applicationContext, final List<Class<?>> typeList) {
        final List<Object> componentList = typeList.stream().map(this.componentContainer::getInstance).flatMap(Optional::stream).toList();

        componentList.forEach(component -> this.initializeComponent(applicationContext, component));

        this.extensionRegistry.onComponentsAttach(applicationContext, componentList);
    }

    public void detach(final ApplicationContext applicationContext, final List<Class<?>> typeList) {
        final List<Object> componentList = new ArrayList<>();

        for (final Class<?> type : typeList.reversed()) {
            this.componentContainer.getInstance(type).ifPresent(component -> {
                this.shutdownComponent(applicationContext, component);

                componentList.add(component);
            });

            this.componentContainer.unregister(type);

            applicationContext.getComponentClassList().remove(type);
        }

        this.extensionRegistry.onComponentsDetach(applicationContext, componentList);
    }

    private void initializeComponent(final ApplicationContext applicationContext, final Object instance) {
        if (instance instanceof final Lifecycle lifecycle) {
            lifecycle.onComponentInitialize();
        }

        applicationContext.getApplicationCallback().onComponentRegister(instance);
    }

    private void shutdownComponent(final ApplicationContext applicationContext, final Object instance) {
        this.extensionRegistry.onComponentShutdown(applicationContext, instance);

        applicationContext.getApplicationCallback().onComponentUnregister(instance);

        if (instance instanceof final Lifecycle lifecycle) {
            lifecycle.onComponentShutdown();
        }
    }
}