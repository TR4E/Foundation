package me.trae.foundation.injector.core.container;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.core.application.ApplicationContext;

import java.lang.reflect.Type;
import java.util.Set;

@AllArgsConstructor
@Getter
public final class ComponentRegistration {

    private final Type type;
    private final Object instance;
    private final ApplicationContext applicationContext;
    private final Type ownerType;
    private final Set<Type> dependencyTypeSet;
}