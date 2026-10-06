package me.trae.foundation.injector.api;

import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.api.exception.ImplementationNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InjectorApiTest {

    private final ApplicationCallback applicationCallback = new ApplicationCallback() {};

    @Test
    void instanceFailsClearlyWithoutAnImplementation() {
        final ExceptionInInitializerError error = assertThrows(ExceptionInInitializerError.class, () -> Injector.INSTANCE.getClass());

        assertInstanceOf(ImplementationNotFoundException.class, error.getCause());
    }

    @Test
    void callbackDefaultsSortByName() {
        assertTrue(this.applicationCallback.getComponentSorter().compare(Integer.class, String.class) < 0);
    }
}