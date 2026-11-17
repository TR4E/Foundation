package me.trae.foundation.database.concurrency;

import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.database.api.converter.ValueConverter;
import me.trae.foundation.database.api.marker.SharedMarker;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

@CustomLog
@AllArgsConstructor
@Getter
public final class Stampede {

    private final SharedMarker marker;
    private final Duration lease, timeout;

    public <Value> Optional<Value> load(final String key, final ValueConverter<Value, String> converter, final Supplier<Optional<Value>> loader) {
        if (this.marker.claim(key, this.lease)) {
            return this.claimed(key, converter, loader);
        }

        return this.marker.await(key, this.timeout)
                .map(payload -> this.decode(payload, converter))
                .orElseGet(() -> {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Await returned no result for {}", key);
                    }
                    return loader.get();
                });
    }

    private <Value> Optional<Value> claimed(final String key, final ValueConverter<Value, String> converter, final Supplier<Optional<Value>> loader) {
        try {
            final Optional<Value> value = loader.get();

            this.marker.release(key, value.map(converter::serialize).orElse(""));

            return value;
        } catch (final RuntimeException exception) {
            this.marker.release(key);

            throw exception;
        }
    }

    private <Value> Optional<Value> decode(final String payload, final ValueConverter<Value, String> converter) {
        return payload.isEmpty() ? Optional.empty() : Optional.of(converter.deserialize(payload));
    }
}