package me.trae.foundation.injector.core;

import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.injector.api.annotation.Singleton;
import me.trae.foundation.injector.api.exception.ApplicationAlreadyInitializedException;
import me.trae.foundation.injector.api.exception.ApplicationNotAnnotatedException;
import me.trae.foundation.injector.api.exception.ApplicationNotInitializedException;
import me.trae.foundation.injector.api.exception.MissingDependencyException;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
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
        this.extensionRegistry.getExtensionList().forEach(extension -> this.componentContainer.register(extension.getClass(), extension));
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

        this.applicationRegistry.pollReady().ifPresent(this::startPending);

        this.notifyLiveDependencyUpdate();
    }

    @Override
    public synchronized void shutdown(final Object application) {
        final Class<?> applicationClass = application.getClass();

        this.applicationRegistry.removePending(applicationClass);

        this.applicationRegistry.getContext(applicationClass).ifPresent(this::stop);

        this.notifyLiveDependencyUpdate();
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

    @Override
    public synchronized void attach(final Object application, final List<Class<?>> componentClasses) {
        final ApplicationContext applicationContext = this.getContext(application.getClass());

        final int index = applicationContext.getComponentClassList().size();

        try {
            new ConstructorResolver(this.componentContainer, this.extensionRegistry, applicationContext, componentClasses.stream().distinct().sorted(applicationContext.getApplicationCallback().getComponentSorter()).toList()).createAll();

            this.componentLifecycle.attach(applicationContext, this.getAttached(applicationContext, index));
        } catch (final RuntimeException | Error exception) {
            try {
                this.componentLifecycle.detach(applicationContext, this.getAttached(applicationContext, index));
            } catch (final RuntimeException | Error detachException) {
                exception.addSuppressed(detachException);
            }

            throw exception;
        }

        this.notifyLiveDependencyUpdate();
    }

    @Override
    public synchronized void detach(final Object application, final List<Class<?>> componentClasses) {
        final ApplicationContext applicationContext = this.getContext(application.getClass());

        this.componentLifecycle.detach(applicationContext, applicationContext.getComponentClassList().stream().filter(componentClasses::contains).toList());

        this.notifyLiveDependencyUpdate();
    }

    private ApplicationContext getContext(final Class<?> applicationClass) {
        return this.applicationRegistry.getContext(applicationClass).orElseThrow(() -> new ApplicationNotInitializedException("%s is not initialized".formatted(applicationClass.getName())));
    }

    private List<Class<?>> getAttached(final ApplicationContext applicationContext, final int index) {
        return List.copyOf(applicationContext.getComponentClassList().subList(index, applicationContext.getComponentClassList().size()));
    }

    private void start(final ApplicationContext applicationContext, final List<Class<?>> componentClasses) {
        this.applicationRegistry.register(applicationContext);

        this.componentContainer.register(applicationContext.getApplicationClass(), applicationContext.getApplication());

        try {
            final List<Class<?>> pendingClassList = Stream.concat(
                    this.scanResolver.resolve(applicationContext.getApplicationClass(),
                            this::isComponent).stream(),
                    componentClasses.stream()
            ).distinct().sorted(applicationContext.getApplicationCallback().getComponentSorter()).toList();

            new ConstructorResolver(this.componentContainer, this.extensionRegistry, applicationContext, pendingClassList).createAll();

            this.componentLifecycle.initialize(applicationContext);
        } catch (final RuntimeException | Error exception) {
            try {
                this.stop(applicationContext);
            } catch (final RuntimeException | Error stopException) {
                exception.addSuppressed(stopException);
            }

            throw exception;
        }
    }

    private void stop(final ApplicationContext applicationContext) {
        if (this.applicationRegistry.getContext(applicationContext.getApplicationClass()).isEmpty()) {
            return;
        }

        this.applicationRegistry.getDependents(applicationContext.getApplicationClass()).forEach(this::stop);

        this.componentLifecycle.shutdown(applicationContext);

        this.componentContainer.unregister(applicationContext.getApplicationClass());

        this.applicationRegistry.unregister(applicationContext.getApplicationClass());
    }

    private void startPending(final PendingApplication pendingApplication) {
        try {
            this.initialize(pendingApplication.getApplication(), pendingApplication.getComponentClasses());
        } catch (final RuntimeException | Error exception) {
            new ApplicationContext(pendingApplication.getApplication()).getApplicationCallback().onApplicationFailure(exception);
        }

        this.applicationRegistry.pollReady().ifPresent(this::startPending);
    }

    private boolean isComponent(final Class<?> type) {
        return type.isAnnotationPresent(Singleton.class) || this.extensionRegistry.isComponent(type);
    }

    private void notifyLiveDependencyUpdate() {
        this.getAll(Lifecycle.class).forEach(Lifecycle::onLiveDependencyUpdate);
    }
}