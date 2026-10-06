package me.trae.foundation.injector.extensions.scheduler.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.extensions.scheduler.annotation.Scheduler;
import me.trae.foundation.injector.extensions.scheduler.exception.SchedulerException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@AllArgsConstructor
@Getter
public final class ScheduledTask {

    private final Object component;
    private final Method method;
    private final Scheduler scheduler;

    public void run() {
        try {
            this.method.invoke(this.component);
        } catch (final ReflectiveOperationException exception) {
            final Thread thread = Thread.currentThread();

            thread.getUncaughtExceptionHandler().uncaughtException(thread, new SchedulerException("Failed to run @Scheduler method %s".formatted(this.getName()), exception instanceof final InvocationTargetException invocationTargetException ? invocationTargetException.getCause() : exception));
        }
    }

    public String getName() {
        return "%s#%s".formatted(this.method.getDeclaringClass().getName(), this.method.getName());
    }
}