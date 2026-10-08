package me.trae.foundation.injector.api.lifecycle;

public interface Lifecycle {

    default void onComponentInitialize() {
    }

    default void onComponentShutdown() {
    }

    default void onLiveDependencyUpdate() {
    }
}