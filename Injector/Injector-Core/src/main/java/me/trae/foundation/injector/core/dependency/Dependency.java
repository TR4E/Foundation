package me.trae.foundation.injector.core.dependency;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.lang.reflect.Type;

@AllArgsConstructor
@Getter
public final class Dependency {

    private final DependencyKind kind;
    private final Type type;
}