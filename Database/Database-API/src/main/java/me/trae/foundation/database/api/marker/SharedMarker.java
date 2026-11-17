package me.trae.foundation.database.api.marker;

import java.time.Duration;
import java.util.Optional;

public interface SharedMarker {

    boolean claim(final String key, final Duration lease);

    Optional<String> await(final String key, final Duration timeout);

    void release(final String key, final String payload);

    void release(final String key);
}