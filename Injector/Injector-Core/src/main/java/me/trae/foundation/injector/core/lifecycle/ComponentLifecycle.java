package me.trae.foundation.injector.core.lifecycle;

import lombok.AllArgsConstructor;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.injector.core.application.ApplicationContext;
import me.trae.foundation.injector.core.container.ComponentContainer;
import me.trae.foundation.injector.core.container.ComponentRegistration;
import me.trae.foundation.injector.core.extension.ExtensionRegistry;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
public final class ComponentLifecycle {

    private final ComponentContainer componentContainer;
    private final ExtensionRegistry extensionRegistry;

    public void initialize(final ApplicationContext applicationContext) {
        for (final Type type : applicationContext.getComponentTypeList()) {
            this.componentContainer.getInstance(type).ifPresent(instance -> this.initializeComponent(applicationContext, instance));
        }

        this.extensionRegistry.onApplicationInitialize(applicationContext);
    }

    public void shutdown(final ApplicationContext applicationContext) {
        final List<Type> typeList = new ArrayList<>(applicationContext.getComponentTypeList());

        typeList.add(applicationContext.getApplicationClass());

        final Map<ApplicationContext, List<Object>> removedComponents = this.remove(applicationContext, typeList, false);

        removedComponents.forEach((context, componentList) -> {
            if (context != applicationContext) {
                this.extensionRegistry.onComponentsDetach(context, componentList);
            }
        });

        this.extensionRegistry.onApplicationShutdown(applicationContext);
    }

    public void attach(final ApplicationContext applicationContext, final List<Type> typeList) {
        final List<Object> componentList = typeList.stream().map(this.componentContainer::getInstance).flatMap(Optional::stream).toList();

        componentList.forEach(component -> this.initializeComponent(applicationContext, component));

        this.extensionRegistry.onComponentsAttach(applicationContext, componentList);
    }

    public void detach(final ApplicationContext applicationContext, final List<Type> typeList) {
        this.remove(applicationContext, typeList, true);
    }

    private Map<ApplicationContext, List<Object>> remove(final ApplicationContext defaultContext, final List<Type> typeList, final boolean notifyDetach) {
        final Map<ApplicationContext, List<Object>> componentMap = new LinkedHashMap<>();

        for (final ComponentRegistration componentRegistration : this.componentContainer.getRemovalOrder(typeList)) {
            final ApplicationContext applicationContext = componentRegistration.getApplicationContext();

            if (applicationContext != null) {
                this.shutdownComponent(applicationContext, componentRegistration.getInstance());

                componentMap.computeIfAbsent(applicationContext, key -> new ArrayList<>()).add(componentRegistration.getInstance());

                applicationContext.getComponentTypeList().remove(componentRegistration.getType());
            }

            this.componentContainer.unregister(componentRegistration.getType());
        }

        if (notifyDetach) {
            componentMap.computeIfAbsent(defaultContext, key -> new ArrayList<>());

            componentMap.forEach(this.extensionRegistry::onComponentsDetach);
        }

        return componentMap;
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