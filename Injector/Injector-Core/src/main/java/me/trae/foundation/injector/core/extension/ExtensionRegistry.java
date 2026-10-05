package me.trae.foundation.injector.core.extension;

import lombok.Getter;
import me.trae.foundation.injector.api.extension.Extension;
import me.trae.foundation.injector.core.application.ApplicationContext;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

@Getter
public final class ExtensionRegistry {

    private final List<Extension> extensionList = ServiceLoader.load(Extension.class, ExtensionRegistry.class.getClassLoader()).stream().map(ServiceLoader.Provider::get).toList();

    public boolean isComponent(final Class<?> type) {
        return this.extensionList.stream().anyMatch(extension -> extension.isComponent(type));
    }

    public Optional<Object> instantiate(final ApplicationContext applicationContext, final Class<?> type) {
        return this.extensionList.stream()
                .<Object>flatMap(extension -> extension.instantiate(applicationContext.getApplicationClass(), applicationContext.getApplicationCallback(), type).stream())
                .findFirst();
    }

    public void onComponentCreate(final ApplicationContext applicationContext, final Object component) {
        this.extensionList.forEach(extension -> {
            extension.onComponentCreate(applicationContext.getApplicationClass(), applicationContext.getApplicationCallback(), component);
        });
    }

    public void onApplicationInitialize(final ApplicationContext applicationContext) {
        this.extensionList.forEach(extension -> {
            extension.onApplicationInitialize(applicationContext.getApplicationClass(), applicationContext.getApplicationCallback());
        });
    }

    public void onComponentShutdown(final ApplicationContext applicationContext, final Object component) {
        this.extensionList.forEach(extension -> {
            extension.onComponentShutdown(applicationContext.getApplicationClass(), applicationContext.getApplicationCallback(), component);
        });
    }

    public void onApplicationShutdown(final ApplicationContext applicationContext) {
        this.extensionList.forEach(extension -> {
            extension.onApplicationShutdown(applicationContext.getApplicationClass(), applicationContext.getApplicationCallback());
        });
    }
}