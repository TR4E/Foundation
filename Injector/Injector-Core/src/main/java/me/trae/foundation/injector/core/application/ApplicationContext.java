package me.trae.foundation.injector.core.application;

import lombok.Getter;
import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.core.resolver.TypeResolver;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@Getter
public final class ApplicationContext {

    private static final ApplicationCallback DEFAULT_CALLBACK = new ApplicationCallback() {};

    private final Object application;
    private final Class<?> applicationClass;
    private final ApplicationCallback applicationCallback;
    private final List<Type> componentTypeList = new ArrayList<>();

    public ApplicationContext(final Object application) {
        this.application = application;
        this.applicationClass = application.getClass();
        this.applicationCallback = application instanceof final ApplicationCallback callback ? callback : DEFAULT_CALLBACK;
    }

    public List<Class<?>> getDependencies() {
        return List.of(this.applicationClass.getAnnotation(Application.class).dependencies());
    }

    public static Class<?> getRawType(final Type type) {
        return TypeResolver.rawType(type);
    }
}