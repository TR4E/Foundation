package me.trae.foundation.injector.core;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.injector.api.annotation.DependsOn;
import me.trae.foundation.injector.api.annotation.Provider;
import me.trae.foundation.injector.api.exception.MissingDependencyException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DependsOnTest {

    private final CoreInjector coreInjector = new CoreInjector();

    @Test
    void skipsComponentWhenAbstractDependencyHasNoImplementation() {
        final int creationCount = Collections.frequency(RecordingExtension.getEvents(TestApplication.class), "create:OptionalListener");
        this.coreInjector.initialize(new TestApplication(), List.of(OptionalListener.class));

        assertThrows(MissingDependencyException.class, () -> this.coreInjector.get(OptionalListener.class));
        assertFalse(this.coreInjector.getComponents(TestApplication.class).contains(OptionalListener.class));
        assertEquals(creationCount, Collections.frequency(RecordingExtension.getEvents(TestApplication.class), "create:OptionalListener"));
    }

    @Test
    void createsAbstractImplementationBeforeDependent() {
        this.coreInjector.initialize(new TestApplication(), List.of(OptionalListener.class, ConcreteManager.class));

        assertSame(this.coreInjector.get(ConcreteManager.class), this.coreInjector.get(OptionalListener.class).getAbstractManager());

        final List<Class<?>> componentList = this.coreInjector.getComponents(TestApplication.class);
        assertTrue(componentList.indexOf(ConcreteManager.class) < componentList.indexOf(OptionalListener.class));
    }

    @Test
    void acceptsAlreadyRegisteredAbstractImplementation() {
        this.coreInjector.initialize(new ManagerApplication(), List.of(ConcreteManager.class));
        this.coreInjector.initialize(new TestApplication(), List.of(OptionalListener.class));

        assertSame(this.coreInjector.get(ConcreteManager.class), this.coreInjector.get(OptionalListener.class).getAbstractManager());
    }

    @Test
    void acceptsProvidedAbstractImplementation() {
        this.coreInjector.initialize(new TestApplication(), List.of(OptionalListener.class, ManagerProvider.class));

        assertSame(this.coreInjector.get(ConcreteManager.class), this.coreInjector.get(OptionalListener.class).getAbstractManager());
    }

    @Test
    void skipsComponentWhenInterfaceDependencyHasNoImplementation() {
        this.coreInjector.initialize(new TestApplication(), List.of(InterfaceListener.class));

        assertThrows(MissingDependencyException.class, () -> this.coreInjector.get(InterfaceListener.class));
    }

    @Test
    void createsAllInterfaceImplementationsBeforeDependent() {
        this.coreInjector.initialize(new TestApplication(), List.of(InterfaceListener.class, FirstFeature.class, SecondFeature.class));

        final List<Class<?>> componentList = this.coreInjector.getComponents(TestApplication.class);
        assertTrue(componentList.indexOf(FirstFeature.class) < componentList.indexOf(InterfaceListener.class));
        assertTrue(componentList.indexOf(SecondFeature.class) < componentList.indexOf(InterfaceListener.class));
        assertEquals(2, this.coreInjector.getAll(Feature.class).size());
    }

    @Test
    void stillRejectsMissingConcreteDependsOnDependency() {
        assertThrows(MissingDependencyException.class, () -> this.coreInjector.initialize(new TestApplication(), List.of(RequiredListener.class)));
    }

    @Test
    void stillRejectsMissingConstructorDependency() {
        assertThrows(MissingDependencyException.class, () -> this.coreInjector.initialize(new TestApplication(), List.of(ConstructorConsumer.class)));
    }

    @Test
    void stillReportsBrokenAbstractImplementation() {
        assertThrows(MissingDependencyException.class, () -> this.coreInjector.initialize(new TestApplication(), List.of(OptionalListener.class, BrokenManager.class)));
    }

    @Test
    void supportsInheritedAbstractDependsOnDependency() {
        this.coreInjector.initialize(new TestApplication(), List.of(InheritedListener.class));

        assertThrows(MissingDependencyException.class, () -> this.coreInjector.get(InheritedListener.class));

        this.coreInjector.attach(new TestApplication(), List.of(InheritedListener.class, ConcreteManager.class));

        assertSame(this.coreInjector.get(ConcreteManager.class), this.coreInjector.get(InheritedListener.class).getAbstractManager());
    }

    @NoArgsConstructor
    @Application
    private static final class TestApplication {

    }

    @NoArgsConstructor
    @Application
    private static final class ManagerApplication {

    }

    @NoArgsConstructor
    private abstract static class AbstractManager<Value> {

    }

    @NoArgsConstructor
    private static final class ConcreteManager extends AbstractManager<String> {

    }

    @AllArgsConstructor
    @Getter
    @DependsOn(AbstractManager.class)
    private static class OptionalListener {

        private final AbstractManager<?> abstractManager;
    }

    @AllArgsConstructor
    private static final class BrokenManager extends AbstractManager<String> {

        private final ConcreteManager concreteManager;
    }

    @NoArgsConstructor
    private static final class ManagerProvider {

        @Provider
        private ConcreteManager concreteManager() {
            return new ConcreteManager();
        }
    }

    @NoArgsConstructor
    @DependsOn(Feature.class)
    private static final class InterfaceListener {

    }

    @NoArgsConstructor
    private static final class FirstFeature implements Feature {

    }

    @NoArgsConstructor
    private static final class SecondFeature implements Feature {

    }

    @NoArgsConstructor
    @DependsOn(ConcreteManager.class)
    private static final class RequiredListener {

    }

    @AllArgsConstructor
    private static final class ConstructorConsumer {

        private final ConcreteManager concreteManager;
    }

    private static final class InheritedListener extends OptionalListener {

        private InheritedListener(final AbstractManager<?> abstractManager) {
            super(abstractManager);
        }
    }

    private interface Feature {

    }
}
