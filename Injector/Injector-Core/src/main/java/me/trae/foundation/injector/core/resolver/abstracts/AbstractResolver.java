package me.trae.foundation.injector.core.resolver.abstracts;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.core.container.ComponentContainer;

@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter(AccessLevel.PROTECTED)
public class AbstractResolver {

    private final ComponentContainer componentContainer;
}