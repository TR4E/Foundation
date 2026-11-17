package me.trae.foundation.database.deep;

import me.trae.foundation.database.redis.event.RedisEvents;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ProtocolBenchmarkTest extends RuntimeFixture {

    @Test
    void currentProtocolPublishToAppliedLatency() {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();
            final AtomicInteger sequence = new AtomicInteger();

            node.getItemHolder().hold(item.getId());

            final Runnable runnable = () -> {
                final int version = sequence.incrementAndGet();
                final String value = "applied-%s".formatted(version);

                node.getRedisDriver().getCommands().publish(
                        channel(item.getId()),
                        "%s\nbenchmark-sender\nUPDATE\n%s".formatted(version, RedisEvents.encode(List.of(set("name", value))))
                );

                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

                while (!value.equals(node.getItemHolder().getHeld().getFirst().getName()) && System.nanoTime() < deadline) {
                    java.util.concurrent.locks.LockSupport.parkNanos(10_000L);
                }

                assertEquals(value, node.getItemHolder().getHeld().getFirst().getName());
            };

            java.util.stream.IntStream.range(0, 50).forEach(_ -> runnable.run());

            final long[] sampleArray = new long[500];
            final long start = System.nanoTime();

            java.util.stream.IntStream.range(0, 500).forEach(index -> {
                final long before = System.nanoTime();

                runnable.run();
                sampleArray[index] = System.nanoTime() - before;
            });

            final long elapsed = System.nanoTime() - start;

            Arrays.sort(sampleArray);
            System.out.println(String.format(Locale.ROOT, "BENCH|publish_to_applied_current_protocol|n=500|p50_us=%.3f|p99_us=%.3f|ops_sec=%.0f", sampleArray[250] / 1000.0, sampleArray[494] / 1000.0, 500_000_000_000.0 / elapsed));
        }
    }
}