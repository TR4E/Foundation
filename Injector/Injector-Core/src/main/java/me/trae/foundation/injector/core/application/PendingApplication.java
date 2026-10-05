package me.trae.foundation.injector.core.application;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.api.annotation.Application;

import java.util.List;

@AllArgsConstructor
@Getter
public final class PendingApplication {

    private final Object application;
    private final List<Class<?>> componentClasses;

    public List<Class<?>> getDependencies() {
        return List.of(this.application.getClass().getAnnotation(Application.class).dependencies());
    }
}