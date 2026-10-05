package me.trae.foundation.injector.core;

import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.injector.api.annotation.Singleton;
import me.trae.foundation.injector.api.exception.ApplicationAlreadyInitializedException;
import me.trae.foundation.injector.api.exception.ApplicationNotAnnotatedException;
import me.trae.foundation.injector.api.exception.ApplicationNotInitializedException;
import me.trae.foundation.injector.api.exception.MissingDependencyException;
import me.trae.foundation.injector.core.application.ApplicationContext;
import me.trae.foundation.injector.core.application.ApplicationRegistry;
import me.trae.foundation.injector.core.application.PendingApplication;
import me.trae.foundation.injector.core.container.ComponentContainer;
import me.trae.foundation.injector.core.extension.ExtensionRegistry;
import me.trae.foundation.injector.core.lifecycle.ComponentLifecycle;
import me.trae.foundation.injector.core.resolver.ConstructorResolver;
import me.trae.foundation.injector.core.resolver.ScanResolver;

import java.util.List;
import java.util.stream.Stream;

public final class CoreInjector implements Injector {

    private final ComponentContainer componentContainer = new ComponentContainer();
    private final ApplicationRegistry applicationRegistry = new ApplicationRegistry();
    private final ExtensionRegistry extensionRegistry = new ExtensionRegistry();
    private final ScanResolver scanResolver = new ScanResolver();
    private final ComponentLifecycle componentLifecycle = new ComponentLifecycle(this.componentContainer, this.extensionRegistry);

    public CoreInjector() {
        this.componentContainer.register(Injector.class, this);
    }

    @Override
    public synchronized void initialize(final Object application, final List<Class<?>> componentClasses) {
        final Class<?> applicationClass = application.getClass();

        final Application annotation = applicationClass.getAnnotation(Application.class);
        if (annotation == null) {
            throw new ApplicationNotAnnotatedException("%s is not annotated with @Application".formatted(applicationClass.getName()));
        }

        if (this.applicationRegistry.isKnown(applicationClass)) {
            throw new ApplicationAlreadyInitializedException("%s is already initialized".formatted(applicationClass.getName()));
        }

        if (!this.applicationRegistry.isReady(List.of(annotation.dependencies()))) {
            this.applicationRegistry.addPending(new PendingApplication(application, componentClasses));
            return;
        }

        this.start(new ApplicationContext(application), componentClasses);

        this.applicationRegistry.pollReady().ifPresent(pendingApplication -> this.initialize(pendingApplication.getApplication(), pendingApplication.getComponentClasses()));
    }

    @Override
    public synchronized void shutdown(final Object application) {
        final Class<?> applicationClass = application.getClass();

        this.applicationRegistry.removePending(applicationClass);

        this.applicationRegistry.getContext(applicationClass).ifPresent(this::stop);
    }

    @Override
    public <T> T get(final Class<T> type) {
        return this.componentContainer.getInstance(type)
                .map(type::cast)
                .orElseThrow(() -> new MissingDependencyException("No component registered for %s".formatted(type.getName())));
    }

    @Override
    public <T> List<T> getAll(final Class<T> type) {
        return this.componentContainer.getAssignable(type).getList().stream().map(type::cast).toList();
    }

    @Override
    public synchronized List<Class<?>> getComponents(final Class<?> applicationClass) {
        return this.applicationRegistry.getContext(applicationClass)
                .map(applicationContext -> List.copyOf(applicationContext.getComponentClassList()))
                .orElseThrow(() -> new ApplicationNotInitializedException("%s is not initialized".formatted(applicationClass.getName())));
    }

    private void start(final ApplicationContext applicationContext, final List<Class<?>> componentClasses) {
        this.applicationRegistry.register(applicationContext);

        this.componentContainer.register(applicationContext.getApplicationClass(), applicationContext.getApplication());

        final List<Class<?>> pendingClassList = Stream.concat(
                this.scanResolver.resolve(applicationContext.getApplicationClass(),
                        this::isComponent).stream(),
                componentClasses.stream()
        ).distinct().sorted(applicationContext.getApplicationCallback().getComponentSorter()).toList();

        new ConstructorResolver(this.componentContainer, this.extensionRegistry, applicationContext, pendingClassList).createAll();

        this.componentLifecycle.initialize(applicationContext);
    }

    private void stop(final ApplicationContext applicationContext) {
        this.applicationRegistry.getDependents(applicationContext.getApplicationClass()).forEach(this::stop);

        this.componentLifecycle.shutdown(applicationContext);

        this.componentContainer.unregister(applicationContext.getApplicationClass());

        this.applicationRegistry.unregister(applicationContext.getApplicationClass());
    }

    private boolean isComponent(final Class<?> type) {
        return type.isAnnotationPresent(Singleton.class) || this.extensionRegistry.isComponent(type);
    }
}