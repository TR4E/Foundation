package me.trae.foundation.injector.extensions.scheduler.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Scheduler {

    int initialDelay() default 0;

    int period();

    TimeUnit unit();

    boolean clock() default false;

    boolean asynchronous() default false;
}