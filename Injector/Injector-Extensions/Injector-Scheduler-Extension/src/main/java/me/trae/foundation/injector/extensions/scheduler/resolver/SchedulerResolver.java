package me.trae.foundation.injector.extensions.scheduler.resolver;

import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.extensions.scheduler.annotation.Scheduler;
import me.trae.foundation.injector.extensions.scheduler.callback.SchedulerCallback;
import me.trae.foundation.injector.extensions.scheduler.exception.SchedulerException;
import me.trae.foundation.injector.extensions.scheduler.task.ScheduledTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class SchedulerResolver {

    private static final SchedulerCallback DEFAULT_CALLBACK = new SchedulerCallback() {};

    private final Map<Object, List<ScheduledFuture<?>>> scheduledFutureMap = new IdentityHashMap<>();
    private final Map<Class<?>, List<ScheduledTask>> pendingTaskMap = new HashMap<>();

    private ScheduledExecutorService scheduledExecutorService;

    public synchronized void collect(final Class<?> applicationClass, final Object component) {
        for (Class<?> type = component.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (final Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Scheduler.class)) {
                    continue;
                }

                this.pendingTaskMap.computeIfAbsent(applicationClass, key -> new ArrayList<>()).add(this.createTask(component, method));
            }
        }
    }

    public synchronized void start(final Class<?> applicationClass, final ApplicationCallback applicationCallback) {
        final List<ScheduledTask> scheduledTaskList = this.pendingTaskMap.remove(applicationClass);
        if (scheduledTaskList == null) {
            return;
        }

        scheduledTaskList.forEach(scheduledTask -> this.schedule(scheduledTask, applicationCallback));
    }

    public synchronized void cancel(final Object component) {
        final List<ScheduledFuture<?>> scheduledFutureList = this.scheduledFutureMap.remove(component);
        if (scheduledFutureList != null) {
            scheduledFutureList.forEach(scheduledFuture -> scheduledFuture.cancel(false));
        }

        if (this.scheduledFutureMap.isEmpty() && this.scheduledExecutorService != null) {
            this.scheduledExecutorService.shutdown();
            this.scheduledExecutorService = null;
        }
    }

    public synchronized void clear(final Class<?> applicationClass) {
        this.pendingTaskMap.remove(applicationClass);
    }

    private ScheduledTask createTask(final Object component, final Method method) {
        final ScheduledTask scheduledTask = new ScheduledTask(component, method, method.getAnnotation(Scheduler.class));

        if (method.getParameterCount() != 0) {
            throw new SchedulerException("@Scheduler method %s must have no parameters".formatted(scheduledTask.getName()));
        }

        if (scheduledTask.getScheduler().unit().toMillis(scheduledTask.getScheduler().period()) <= 0) {
            throw new SchedulerException("@Scheduler method %s must have a positive period".formatted(scheduledTask.getName()));
        }

        method.setAccessible(true);

        return scheduledTask;
    }

    private void schedule(final ScheduledTask scheduledTask, final ApplicationCallback applicationCallback) {
        final Scheduler scheduler = scheduledTask.getScheduler();

        final long period = scheduler.unit().toMillis(scheduler.period());
        final long initialDelay = scheduler.clock() ? period - System.currentTimeMillis() % period : scheduler.initialDelay() > 0 ? scheduler.unit().toMillis(scheduler.initialDelay()) : period;

        final SchedulerCallback schedulerCallback = applicationCallback instanceof final SchedulerCallback callback ? callback : DEFAULT_CALLBACK;

        final Executor executor = scheduler.asynchronous() ? schedulerCallback.getAsynchronousExecutor() : schedulerCallback.getSynchronousExecutor();

        final ScheduledFuture<?> scheduledFuture = this.getScheduledExecutorService().scheduleAtFixedRate(() -> {
            executor.execute(scheduledTask::run);
        }, initialDelay, period, TimeUnit.MILLISECONDS);

        this.scheduledFutureMap.computeIfAbsent(scheduledTask.getComponent(), key -> new ArrayList<>()).add(scheduledFuture);
    }

    private ScheduledExecutorService getScheduledExecutorService() {
        if (this.scheduledExecutorService == null) {
            this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("injector-scheduler").factory());
        }

        return this.scheduledExecutorService;
    }
}