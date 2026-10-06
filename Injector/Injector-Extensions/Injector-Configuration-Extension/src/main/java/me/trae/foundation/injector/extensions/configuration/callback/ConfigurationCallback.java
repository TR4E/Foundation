package me.trae.foundation.injector.extensions.configuration.callback;

import java.io.File;

public interface ConfigurationCallback {

    default File getDataFolder() {
        return new File("");
    }

    default void onConfigurationSave(final Class<?> type) {
    }

    default void onConfigurationReload(final Class<?> type) {
    }
}