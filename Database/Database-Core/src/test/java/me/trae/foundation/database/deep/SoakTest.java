package me.trae.foundation.database.deep;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class SoakTest extends RuntimeFixture {

    @TempDir
    private Path recordingDirectory;

    @Test
    void mixedReadWriteWorkloadForSixtySeconds() throws Exception {
        final com.sun.management.ThreadMXBean threadMXBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

        assertTrue(threadMXBean.isThreadAllocatedMemorySupported());
        threadMXBean.setThreadAllocatedMemoryEnabled(true);

        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Recording recording = new Recording()) {
            final List<Item> itemList = IntStream.range(0, 8).mapToObj(_ -> firstNode.seed()).toList();

            for (final Item item : itemList) {
                firstNode.getItemHolder().hold(item.getId());
                secondNode.getItemHolder().hold(item.getId());
            }

            final LongAdder[] windowArray = IntStream.range(0, 6).mapToObj(_ -> new LongAdder()).toArray(LongAdder[]::new);
            final LongAdder readCount = new LongAdder();
            final LongAdder writeCount = new LongAdder();
            final LongAdder errorCount = new LongAdder();
            final AtomicLong firstErrorTime = new AtomicLong();
            final java.util.concurrent.atomic.AtomicReference<String> firstError = new java.util.concurrent.atomic.AtomicReference<>();

            recording.enable("jdk.GCPhasePause").withThreshold(Duration.ZERO);
            recording.start();

            final long allocatedBefore = threadMXBean.getTotalThreadAllocatedBytes();
            final long start = System.nanoTime();
            final long deadline = start + TimeUnit.SECONDS.toNanos(60);

            try (final var executorService = Executors.newFixedThreadPool(8)) {
                final List<java.util.concurrent.Future<?>> futureList = IntStream.range(0, 8)
                        .mapToObj(worker -> executorService.submit(() -> {
                            final Node node = worker % 2 == 0 ? firstNode : secondNode;
                            final AtomicLong iteration = new AtomicLong();

                            while (System.nanoTime() < deadline) {
                                final long sequence = iteration.getAndIncrement();

                                try {
                                    if (sequence % 4 == 0) {
                                        node.getItemHolder().increment(
                                                node.getItemHolder().get(itemList.get(worker).getId()).orElseThrow(),
                                                ItemProperty.COUNTER,
                                                1L
                                        );
                                        writeCount.increment();
                                    } else {
                                        node.getItemHolder().get(itemList.get((int) (sequence % itemList.size())).getId()).orElseThrow();
                                        readCount.increment();
                                    }

                                    windowArray[Math.min(5, (int) ((System.nanoTime() - start) / TimeUnit.SECONDS.toNanos(10)))].increment();
                                } catch (final RuntimeException exception) {
                                    errorCount.increment();
                                    firstError.compareAndSet(null, exception.toString());
                                    firstErrorTime.compareAndSet(0L, System.nanoTime() - start);
                                }
                            }
                        }))
                        .collect(java.util.stream.Collectors.toList());

                for (final var future : futureList) {
                    future.get(70, TimeUnit.SECONDS);
                }
            }

            final double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
            final long allocatedBytes = threadMXBean.getTotalThreadAllocatedBytes() - allocatedBefore;

            recording.stop();

            final Path recordingPath = this.recordingDirectory.resolve("soak.jfr");

            recording.dump(recordingPath);

            final List<Double> pauseList = new ArrayList<>();

            try (final RecordingFile recordingFile = new RecordingFile(recordingPath)) {
                while (recordingFile.hasMoreEvents()) {
                    final var recordedEvent = recordingFile.readEvent();

                    if (recordedEvent.getEventType().getName().equals("jdk.GCPhasePause")) {
                        pauseList.add(recordedEvent.getDuration().toNanos() / 1_000_000.0);
                    }
                }
            }

            firstNode.flush();
            secondNode.flush();

            final double drift = (windowArray[5].sum() / (double) Math.max(1L, windowArray[0].sum()) - 1.0) * 100.0;

            IntStream.range(
                    0,
                    6
            ).forEach(index -> System.out.println("SOAK_WINDOW|index=%s|seconds=10|ops=%s|ops_sec=%s".formatted(index, windowArray[index].sum(), windowArray[index].sum() / 10.0)));
            System.out.println(String.format(Locale.ROOT, "SOAK|seconds=%.3f|reads=%d|writes=%d|errors=%d|drift_pct=%.3f|allocation_mib_sec=%.3f|gc_pauses=%d|gc_total_ms=%.3f|gc_max_ms=%.3f|pending=%d|first_error=%s", elapsedSeconds, readCount.sum(), writeCount.sum(), errorCount.sum(), drift, allocatedBytes / elapsedSeconds / 1048576.0, pauseList.size(), pauseList.stream().mapToDouble(Double::doubleValue).sum(), pauseList.stream().mapToDouble(Double::doubleValue).max().orElse(0.0), firstNode.getCoreDatabase().getBatchQueue().size() + secondNode.getCoreDatabase().getBatchQueue().size(), firstError.get()));

            assertTrue(elapsedSeconds >= 60.0);
            assertEquals(0L, errorCount.sum(), "Soak workload raised runtime errors");
        }
    }
}