package me.trae.foundation.database.verification;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@UtilityClass
public final class BenchmarkSupport {

    public static void latency(final String name, final int warmup, final int samples, final Runnable runnable) {
        IntStream.range(0, warmup).forEach(_ -> runnable.run());

        final long[] sampleArray = new long[samples];
        final long start = System.nanoTime();

        IntStream.range(0, samples).forEach(index -> {
            final long before = System.nanoTime();

            runnable.run();
            sampleArray[index] = System.nanoTime() - before;
        });

        final long elapsed = System.nanoTime() - start;

        Arrays.sort(sampleArray);
        System.out.println(String.format(Locale.ROOT, "BENCH|%s|n=%d|p50_us=%.3f|p99_us=%.3f|ops_sec=%.0f", name, samples, sampleArray[samples / 2] / 1000.0, sampleArray[Math.min(samples - 1, (int) Math.ceil(samples * 0.99) - 1)] / 1000.0, samples * 1_000_000_000.0 / elapsed));
    }

    public static void throughput(final String name, final int threads, final int operationsPerThread, final Runnable runnable) throws Exception {
        IntStream.range(0, 100_000).forEach(_ -> runnable.run());

        final CountDownLatch ready = new CountDownLatch(threads);
        final CountDownLatch go = new CountDownLatch(1);

        try (final var executorService = Executors.newFixedThreadPool(threads)) {
            final List<java.util.concurrent.Future<?>> futureList = IntStream.range(0, threads)
                    .mapToObj(_ -> executorService.submit(() -> {
                        ready.countDown();

                        try {
                            go.await();
                        } catch (final InterruptedException exception) {
                            Thread.currentThread().interrupt();

                            throw new AssertionError(exception);
                        }

                        IntStream.range(0, operationsPerThread).forEach(_ -> runnable.run());
                    }))
                    .collect(java.util.stream.Collectors.toList());

            org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));

            final long start = System.nanoTime();

            go.countDown();

            for (final var future : futureList) {
                future.get(30, TimeUnit.SECONDS);
            }

            System.out.println(String.format(Locale.ROOT, "BENCH|%s|threads=%d|n=%d|ops_sec=%.0f", name, threads, (long) threads * operationsPerThread, threads * (double) operationsPerThread * 1_000_000_000.0 / (System.nanoTime() - start)));
        }
    }
}