package me.trae.foundation.spring.ratelimit.annotation;

import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.ratelimit.scope.RateLimitTarget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface RateLimitShared {

    String name() default "";

    RateLimitTarget target();

    RateLimitScope scope();

    int attempts();

    int duration();

    TimeUnit unit();
}