package me.trae.foundation.injector.extensions.scheduler;

import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.core.CoreInjector;
import me.trae.foundation.injector.extensions.scheduler.annotation.Scheduler;
import me.trae.foundation.injector.extensions.scheduler.callback.SchedulerCallback;
import me.trae.foundation.injector.extensions.scheduler.exception.SchedulerException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SchedulerExtensionTest {

    private final CoreInjector injector = new CoreInjector();

    private final SchedulerCallback schedulerCallback = new SchedulerCallback() {};

    @Test
    void synchronousExecutorRunsInline() {
        final AtomicBoolean ran = new AtomicBoolean();

        this.schedulerCallback.getSynchronousExecutor().execute(() -> ran.set(true));

        assertTrue(ran.get());
    }

    @Test
    void asynchronousExecutorUsesVirtualThreads() throws Exception {
        final CompletableFuture<Thread> thread = new CompletableFuture<>();

        this.schedulerCallback.getAsynchronousExecutor().execute(() -> thread.complete(Thread.currentThread()));

        assertTrue(thread.get(2, TimeUnit.SECONDS).isVirtual());
    }

    @Test
    void runsScheduledMethodRepeatedly() throws InterruptedException {
        final TestApplication application = new TestApplication();

        this.injector.initialize(application, List.of(Ticker.class));

        assertTrue(this.injector.get(Ticker.class).latch.await(2, TimeUnit.SECONDS));
        this.injector.shutdown(application);
    }

    @Test
    void stopsRunningAfterShutdown() throws InterruptedException {
        final TestApplication application = new TestApplication();

        this.injector.initialize(application, List.of(Ticker.class));

        final Ticker ticker = this.injector.get(Ticker.class);
        assertTrue(ticker.latch.await(2, TimeUnit.SECONDS));

        this.injector.shutdown(application);
        Thread.sleep(60);

        final int count = ticker.count.get();
        Thread.sleep(200);
        assertEquals(count, ticker.count.get());
    }

    @Test
    void usesAsynchronousExecutorWhenAsked() throws InterruptedException {
        final TestApplication application = new TestApplication();

        this.injector.initialize(application, List.of(AsynchronousTicker.class));

        assertTrue(this.injector.get(AsynchronousTicker.class).latch.await(2, TimeUnit.SECONDS));
        assertTrue(application.asynchronousCount.get() > 0);
        this.injector.shutdown(application);
    }

    @Test
    void keepsRunningAfterAFailedRun() throws InterruptedException {
        final TestApplication application = new TestApplication();

        this.injector.initialize(application, List.of(FlakyTicker.class));

        assertTrue(this.injector.get(FlakyTicker.class).latch.await(2, TimeUnit.SECONDS));
        this.injector.shutdown(application);
    }

    @Test
    void shuttingDownOneApplicationKeepsOthersRunning() throws InterruptedException {
        final TestApplication first = new TestApplication();
        final SecondApplication second = new SecondApplication();

        this.injector.initialize(first, List.of(Ticker.class));
        this.injector.initialize(second, List.of(SecondTicker.class));
        this.injector.shutdown(first);

        final SecondTicker secondTicker = this.injector.get(SecondTicker.class);
        final int count = secondTicker.count.get();
        Thread.sleep(200);

        assertTrue(secondTicker.count.get() >= count + 3);
        this.injector.shutdown(second);
    }

    @Test
    void schedulesAgainAfterEverythingShutDown() throws InterruptedException {
        final TestApplication application = new TestApplication();

        this.injector.initialize(application, List.of(Ticker.class));
        this.injector.shutdown(application);
        this.injector.initialize(application, List.of(Ticker.class));

        assertTrue(this.injector.get(Ticker.class).latch.await(2, TimeUnit.SECONDS));
        this.injector.shutdown(application);
    }

    @Test
    void waitsForInitialDelay() throws InterruptedException {
        final TestApplication application = new TestApplication();

        this.injector.initialize(application, List.of(DelayedTicker.class));

        final DelayedTicker delayedTicker = this.injector.get(DelayedTicker.class);
        Thread.sleep(150);
        assertEquals(3, delayedTicker.latch.getCount());

        assertTrue(delayedTicker.latch.await(2, TimeUnit.SECONDS));
        this.injector.shutdown(application);
    }

    @Test
    void rejectsSchedulerWithParameters() {
        assertThrows(SchedulerException.class, () -> this.injector.initialize(new TestApplication(), List.of(ParameterTicker.class)));
    }

    @Test
    void rejectsNonPositivePeriod() {
        assertThrows(SchedulerException.class, () -> this.injector.initialize(new TestApplication(), List.of(ZeroTicker.class)));
    }

    @Application
    private static final class TestApplication implements ApplicationCallback, SchedulerCallback {

        private final AtomicInteger asynchronousCount = new AtomicInteger();

        @Override
        public Executor getAsynchronousExecutor() {
            return runnable -> {
                this.asynchronousCount.incrementAndGet();
                Thread.ofVirtual().start(runnable);
            };
        }
    }

    private static final class Ticker {

        private final CountDownLatch latch = new CountDownLatch(3);
        private final AtomicInteger count = new AtomicInteger();

        @Scheduler(period = 20, unit = TimeUnit.MILLISECONDS)
        private void tick() {
            this.count.incrementAndGet();
            this.latch.countDown();
        }
    }

    @Application
    private static final class SecondApplication {
    }

    private static final class SecondTicker {

        private final AtomicInteger count = new AtomicInteger();

        @Scheduler(period = 20, unit = TimeUnit.MILLISECONDS)
        private void tick() {
            this.count.incrementAndGet();
        }
    }

    private static final class DelayedTicker {

        private final CountDownLatch latch = new CountDownLatch(3);

        @Scheduler(initialDelay = 400, period = 20, unit = TimeUnit.MILLISECONDS)
        private void tick() {
            this.latch.countDown();
        }
    }

    private static final class AsynchronousTicker {

        private final CountDownLatch latch = new CountDownLatch(3);

        @Scheduler(period = 20, unit = TimeUnit.MILLISECONDS, asynchronous = true)
        private void tick() {
            this.latch.countDown();
        }
    }

    private static final class FlakyTicker {

        private final CountDownLatch latch = new CountDownLatch(3);

        @Scheduler(period = 20, unit = TimeUnit.MILLISECONDS)
        private void tick() {
            this.latch.countDown();

            if (this.latch.getCount() == 2) {
                throw new IllegalStateException("First run fails on purpose");
            }
        }
    }

    private static final class ParameterTicker {

        @Scheduler(period = 20, unit = TimeUnit.MILLISECONDS)
        private void tick(final String value) {
        }
    }

    private static final class ZeroTicker {

        @Scheduler(period = 0, unit = TimeUnit.MILLISECONDS)
        private void tick() {
        }
    }
}