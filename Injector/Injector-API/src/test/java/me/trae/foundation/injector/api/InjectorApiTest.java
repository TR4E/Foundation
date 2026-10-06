package me.trae.foundation.injector.api;

import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.api.exception.ImplementationNotFoundException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InjectorApiTest {

    private final ApplicationCallback applicationCallback = new ApplicationCallback() {
    };

    @Test
    void instanceFailsClearlyWithoutAnImplementation() {
        final ExceptionInInitializerError error = assertThrows(ExceptionInInitializerError.class, () -> Injector.INSTANCE.getClass());

        assertInstanceOf(ImplementationNotFoundException.class, error.getCause());
    }

    @Test
    void callbackDefaultsRunInlineAndKeepOrder() {
        final AtomicBoolean ran = new AtomicBoolean();

        this.applicationCallback.getSynchronousExecutor().execute(() -> ran.set(true));

        assertTrue(ran.get());
        assertEquals(Path.of(""), this.applicationCallback.getDataFolder());
        assertEquals(0, this.applicationCallback.getComponentSorter().compare(String.class, Integer.class));
    }

    @Test
    void asynchronousExecutorUsesVirtualThreads() throws Exception {
        final CompletableFuture<Thread> thread = new CompletableFuture<>();

        this.applicationCallback.getAsynchronousExecutor().execute(() -> thread.complete(Thread.currentThread()));

        assertTrue(thread.get(2, TimeUnit.SECONDS).isVirtual());
    }
}
