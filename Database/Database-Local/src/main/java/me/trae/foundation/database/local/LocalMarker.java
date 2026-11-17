package me.trae.foundation.database.local;

import me.trae.foundation.database.api.marker.SharedMarker;

import java.time.Duration;
import java.util.Optional;

public final class LocalMarker implements SharedMarker {

    @Override
    public boolean claim(final String key, final Duration lease) {
        return true;
    }

    @Override
    public Optional<String> await(final String key, final Duration timeout) {
        return Optional.empty();
    }

    @Override
    public void release(final String key, final String payload) {
    }

    @Override
    public void release(final String key) {
    }
}