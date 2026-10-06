package me.trae.foundation.injector.extensions.commons;

import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.injector.core.CoreInjector;
import me.trae.foundation.injector.extensions.commons.annotation.ApplicationReady;
import me.trae.foundation.injector.extensions.commons.annotation.PostConstruct;
import me.trae.foundation.injector.extensions.commons.annotation.PostDestroy;
import me.trae.foundation.injector.extensions.commons.annotation.PreDestroy;
import me.trae.foundation.injector.extensions.commons.exception.LifecycleException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CommonsExtensionTest {

    private final CoreInjector injector = new CoreInjector();

    @Test
    void runsAnnotatedMethodsAroundTheLifecycle() {
        final TestApplication application = new TestApplication();

        this.injector.initialize(application, List.of(Tracked.class));
        this.injector.shutdown(application);

        assertEquals(List.of("postConstruct", "initialize", "applicationReady", "preDestroy", "shutdown", "postDestroy"), application.eventList);
    }

    @Test
    void runsPostDestroyInReverseCreationOrder() {
        final TestApplication application = new TestApplication();

        this.injector.initialize(application, List.of(First.class, Second.class));
        this.injector.shutdown(application);

        assertEquals(List.of("postDestroy:Second", "postDestroy:First"), application.eventList);
    }

    @Test
    void runsAnnotatedMethodsDeclaredOnSuperclass() {
        final TestApplication application = new TestApplication();

        this.injector.initialize(application, List.of(Child.class));

        assertEquals(List.of("postConstruct:Parent"), application.eventList);
    }

    @Test
    void rejectsAnnotatedMethodWithParameters() {
        assertThrows(LifecycleException.class, () -> this.injector.initialize(new TestApplication(), List.of(Invalid.class)));
    }

    @Application
    private static final class TestApplication {

        private final List<String> eventList = new ArrayList<>();
    }

    private record Tracked(
            TestApplication application
    ) implements Lifecycle {

        @PostConstruct
        private void postConstruct() {
            this.application.eventList.add("postConstruct");
        }

        @Override
        public void onComponentInitialize() {
            this.application.eventList.add("initialize");
        }

        @ApplicationReady
        private void applicationReady() {
            this.application.eventList.add("applicationReady");
        }

        @PreDestroy
        private void preDestroy() {
            this.application.eventList.add("preDestroy");
        }

        @Override
        public void onComponentShutdown() {
            this.application.eventList.add("shutdown");
        }

        @PostDestroy
        private void postDestroy() {
            this.application.eventList.add("postDestroy");
        }
    }

    private record First(
            TestApplication application
    ) {

        @PostDestroy
        private void postDestroy() {
            this.application.eventList.add("postDestroy:First");
        }
    }

    private record Second(
            TestApplication application
    ) {

        @PostDestroy
        private void postDestroy() {
            this.application.eventList.add("postDestroy:Second");
        }
    }

    private static class Parent {

        protected final TestApplication application;

        private Parent(final TestApplication application) {
            this.application = application;
        }

        @PostConstruct
        private void parentSetup() {
            this.application.eventList.add("postConstruct:Parent");
        }
    }

    private static final class Child extends Parent {

        private Child(final TestApplication application) {
            super(application);
        }
    }

    private record Invalid() {

        @PostConstruct
        private void setup(final String value) {
        }
    }
}
