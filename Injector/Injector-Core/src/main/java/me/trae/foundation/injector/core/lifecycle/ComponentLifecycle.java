package me.trae.foundation.injector.core.lifecycle;

import lombok.AllArgsConstructor;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.injector.core.application.ApplicationContext;
import me.trae.foundation.injector.core.container.ComponentContainer;
import me.trae.foundation.injector.core.extension.ExtensionRegistry;

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