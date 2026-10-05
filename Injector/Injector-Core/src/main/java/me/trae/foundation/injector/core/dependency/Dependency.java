package me.trae.foundation.injector.core.dependency;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public final class Dependency {

    private final DependencyKind kind;
    private final Class<?> type;
}