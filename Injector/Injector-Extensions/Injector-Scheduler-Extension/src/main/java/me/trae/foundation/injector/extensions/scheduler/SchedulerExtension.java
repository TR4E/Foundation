package me.trae.foundation.injector.extensions.scheduler;

import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.api.extension.Extension;
import me.trae.foundation.injector.extensions.scheduler.resolver.SchedulerResolver;

public final class SchedulerExtension implements Extension {

    private final SchedulerResolver schedulerResolver = new SchedulerResolver();

    @Override
    public void onApplicationInitialize(final Class<?> applicationClass, final ApplicationCallback applicationCallback) {
        this.schedulerResolver.start(applicationClass, applicationCallback);
    }

    @Override
    public void onApplicationShutdown(final Class<?> applicationClass, final ApplicationCallback applicationCallback) {
        this.schedulerResolver.clear(applicationClass);
    }

    @Override
    public void onComponentCreate(final Class<?> applicationClass, final ApplicationCallback applicationCallback, final Object component) {
        this.schedulerResolver.collect(applicationClass, component);
    }

    @Override
    public void onComponentShutdown(final Class<?> applicationClass, final ApplicationCallback applicationCallback, final Object component) {
        this.schedulerResolver.cancel(component);
    }
}