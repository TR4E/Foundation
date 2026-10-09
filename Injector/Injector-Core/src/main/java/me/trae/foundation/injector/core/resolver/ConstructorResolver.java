package me.trae.foundation.injector.core.resolver;

import me.trae.foundation.injector.api.exception.CircularDependencyException;
import me.trae.foundation.injector.api.exception.ComponentCreationException;
import me.trae.foundation.injector.api.exception.ConstructorException;
import me.trae.foundation.injector.api.exception.MissingDependencyException;
import me.trae.foundation.injector.core.application.ApplicationContext;
import me.trae.foundation.injector.core.container.ComponentContainer;
import me.trae.foundation.injector.core.container.ComponentRegistration;
import me.trae.foundation.injector.core.extension.ExtensionRegistry;
import me.trae.foundation.injector.core.resolver.abstracts.AbstractResolver;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ConstructorResolver extends AbstractResolver {

    private final Set<Class<?>> resolvingSet = new LinkedHashSet<>();
    private final Map<Type, List<Class<?>>> assignableClassCacheMap = new LinkedHashMap<>();

    private final ExtensionRegistry extensionRegistry;
    private final ApplicationContext applicationContext;
    private final List<Class<?>> pendingClassList;
    private final DependencyResolver dependencyResolver;
    private final DependsOnResolver dependsOnResolver;
    private final ProviderResolver providerResolver;

    public ConstructorResolver(final ComponentContainer componentContainer, final ExtensionRegistry extensionRegistry, final ApplicationContext applicationContext, final List<Class<?>> pendingClassList) {
        super(componentContainer);

        this.extensionRegistry = extensionRegistry;
        this.applicationContext = applicationContext;
        this.pendingClassList = pendingClassList;
        this.dependencyResolver = new DependencyResolver(componentContainer);
        this.dependsOnResolver = new DependsOnResolver();
        this.providerResolver = new ProviderResolver(componentContainer, extensionRegistry, applicationContext, this.dependencyResolver, pendingClassList);
    }

    public void createAll() {
        this.pendingClassList.forEach(this::create);
    }

    private void create(final Class<?> type) {
        if (this.getComponentContainer().isRegistered(type)) {
            return;
        }

        if (!this.resolvingSet.add(type)) {
            throw new CircularDependencyException("Circular dependency detected: %s".formatted(this.getChain(type)));
        }

        try {
            final LinkedHashSet<Type> dependencyTypeSet = new LinkedHashSet<>();

            this.dependsOnResolver.resolve(type).forEach(dependency -> this.require(type, dependency, dependencyTypeSet));

            final Object instance = this.extensionRegistry.instantiate(this.applicationContext, type).orElseGet(() -> this.construct(type, dependencyTypeSet));

            this.getComponentContainer().register(type, instance, this.applicationContext, null, dependencyTypeSet);

            this.applicationContext.getComponentTypeList().add(type);

            this.extensionRegistry.onComponentCreate(this.applicationContext, instance);

            this.providerResolver.provide(instance, type, this::ensure);
        } finally {
            this.resolvingSet.remove(type);
        }
    }

    private void ensure(final Type dependencyType) {
        this.providerResolver.findExactOwner(dependencyType).ifPresent(this::create);

        if (this.getComponentContainer().getInstance(dependencyType).isPresent()) {
            return;
        }

        final List<Class<?>> candidateList = this.assignableClassCacheMap.computeIfAbsent(dependencyType, requestedType -> this.pendingClassList.stream()
                .filter(candidate -> TypeResolver.isAssignable(requestedType, candidate))
                .toList());

        candidateList.forEach(this::create);

        this.providerResolver.findOwner(dependencyType).ifPresent(this::create);
    }

    private void require(final Class<?> type, final Class<?> dependency, final Set<Type> dependencyTypeSet) {
        this.ensure(dependency);

        final List<ComponentRegistration> registrationList = this.getComponentContainer().getAssignableRegistrations(dependency);

        if (registrationList.isEmpty()) {
            throw new MissingDependencyException("%s depends on %s but none was found".formatted(type.getName(), dependency.getName()));
        }

        registrationList.stream().map(ComponentRegistration::getType).forEach(dependencyTypeSet::add);
    }

    private Object construct(final Class<?> type, final Set<Type> dependencyTypeSet) {
        final Constructor<?>[] constructors = type.getDeclaredConstructors();

        if (constructors.length != 1) {
            throw new ConstructorException("%s must declare exactly one constructor".formatted(type.getName()));
        }

        final Constructor<?> constructor = constructors[0];

        final Object[] arguments = this.dependencyResolver.resolveArguments(constructor.getGenericParameterTypes(), this::ensure, dependencyTypeSet);

        try {
            constructor.setAccessible(true);

            return constructor.newInstance(arguments);
        } catch (final ReflectiveOperationException exception) {
            throw new ComponentCreationException("Failed to create %s".formatted(type.getName()), exception instanceof final InvocationTargetException invocationTargetException ? invocationTargetException.getCause() : exception);
        }
    }

    private String getChain(final Class<?> type) {
        return Stream.concat(
                this.resolvingSet.stream().dropWhile(entry -> entry != type),
                Stream.of(type)
        ).map(Class::getSimpleName).collect(Collectors.joining(" -> "));
    }
}