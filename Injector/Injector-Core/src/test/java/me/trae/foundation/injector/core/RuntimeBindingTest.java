package me.trae.foundation.injector.core;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.injector.api.exception.MissingDependencyException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RuntimeBindingTest {

    private final CoreInjector injector = new CoreInjector();

    @Test
    void injectsProvidedInstancesWithoutAddingThemToOverrideLists() {
        final Runnable runnable = () -> {};
        this.injector.provide(Runnable.class, runnable);
        this.injector.initialize(new TestApplication(), List.of(RunnableConsumer.class));

        assertSame(runnable, this.injector.get(Runnable.class));
        assertSame(runnable, this.injector.get(RunnableConsumer.class).getRunnable());
        assertEquals(List.of(), this.injector.getAll(Runnable.class));
    }

    @Test
    void replacesAnExplicitBindingWithoutReplacingConcreteComponents() {
        this.injector.initialize(new TestApplication(), List.of(FirstRunnable.class));

        final Runnable runnable = () -> {};
        this.injector.provide(Runnable.class, runnable);
        this.injector.provide(Runnable.class, this.injector.get(FirstRunnable.class));

        assertSame(this.injector.get(FirstRunnable.class), this.injector.get(Runnable.class));
        assertEquals(List.of(this.injector.get(FirstRunnable.class)), this.injector.getAll(Runnable.class));
    }

    @Test
    void removesAnExplicitBindingWithoutRemovingConcreteComponents() {
        this.injector.initialize(new TestApplication(), List.of(FirstRunnable.class));
        this.injector.provide(Runnable.class, this.injector.get(FirstRunnable.class));
        this.injector.unprovide(Runnable.class);

        assertThrows(MissingDependencyException.class, () -> this.injector.get(Runnable.class));
        assertEquals(1, this.injector.getAll(Runnable.class).size());
    }

    @AllArgsConstructor
    @Getter
    private static final class RunnableConsumer {

        private final Runnable runnable;
    }

    @NoArgsConstructor
    private static final class FirstRunnable implements Runnable {

        @Override
        public void run() {
        }
    }

    @NoArgsConstructor
    @Application
    private static final class TestApplication {

    }
}