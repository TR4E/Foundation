package me.trae.foundation.spring.ratelimit.annotation;

import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimitShared {

    String name() default "";

    RateLimitScope scope();

    int attempts();

    int duration();

    TimeUnit unit();
}