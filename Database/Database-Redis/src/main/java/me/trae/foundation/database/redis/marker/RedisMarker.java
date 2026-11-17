package me.trae.foundation.database.redis.marker;

import io.lettuce.core.KeyValue;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.marker.SharedMarker;
import me.trae.foundation.database.redis.driver.RedisDriver;
import me.trae.foundation.database.redis.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Getter
public final class RedisMarker implements SharedMarker {

    private static final RedisScript RELEASE = new RedisScript("if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end");

    private static final Duration POLL = Duration.ofMillis(5);
    private static final Duration RESULT_TTL = Duration.ofSeconds(2);

    private final RedisDriver driver;

    @Override
    public boolean claim(final String key, final Duration lease) {
        return "OK".equals(this.driver.getCommands().set(this.claimKey(key), this.driver.getInstanceId(), SetArgs.Builder.nx().px(lease.toMillis())));
    }

    @Override
    public Optional<String> await(final String key, final Duration timeout) {
        final long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            final List<KeyValue<String, String>> values = this.driver.getCommands().mget(this.resultKey(key), this.claimKey(key));

            if (values.getFirst().hasValue()) {
                return Optional.of(values.getFirst().getValue());
            }

            if (!values.get(1).hasValue() || !this.sleep()) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    @Override
    public void release(final String key, final String payload) {
        this.driver.getCommands().set(this.resultKey(key), payload, SetArgs.Builder.px(RESULT_TTL.toMillis()));

        this.release(key);
    }

    @Override
    public void release(final String key) {
        RELEASE.execute(this.driver, ScriptOutputType.INTEGER, new String[]{this.claimKey(key)}, this.driver.getInstanceId());
    }

    private boolean sleep() {
        try {
            Thread.sleep(POLL);

            return true;
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String claimKey(final String key) {
        return key + ":claim";
    }

    private String resultKey(final String key) {
        return key + ":result";
    }
}