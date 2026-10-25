package me.trae.foundation.database.core;

import lombok.experimental.UtilityClass;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@UtilityClass
public class DatabaseTestEnvironment {

    private static final int CONNECT_TIMEOUT_MILLIS = 500;

    public static void requirePostgres() {
        requirePort("PostgreSQL", System.getProperty("postgres.host", "localhost"), Integer.getInteger("postgres.port", 5432));
    }

    public static void requirePostgresAndRedis() {
        requirePostgres();
        requirePort("Redis", System.getProperty("redis.host", "localhost"), Integer.getInteger("redis.port", 6379));
    }

    public static void requireRunningDockerContainers(final String... containerNames) throws InterruptedException {
        final List<String> commandList = new ArrayList<>(List.of("docker", "inspect", "--format", "{{.State.Running}}"));
        commandList.addAll(List.of(containerNames));

        try {
            final Process process = new ProcessBuilder(commandList).redirectErrorStream(true).start();

            try {
                Assumptions.assumeTrue(process.waitFor(5L, TimeUnit.SECONDS), "Docker availability check timed out");
                Assumptions.assumeTrue(
                        process.exitValue() == 0,
                        "Docker is unavailable or the configured audit containers do not exist"
                );
                final List<String> stateList = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        .lines()
                        .toList();
                Assumptions.assumeTrue(
                        stateList.size() == containerNames.length && stateList.stream().allMatch("true"::equals),
                        "The configured audit containers must already be running"
                );
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } catch (final IOException exception) {
            Assumptions.abort("Docker CLI is unavailable: %s".formatted(exception.getClass().getSimpleName()));
        }
    }

    static void requirePort(final String service, final String host, final int port) {
        try (final Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        } catch (final IOException exception) {
            Assumptions.abort("%s is unavailable at %s:%d (%s)".formatted(
                    service,
                    host,
                    port,
                    exception.getClass().getSimpleName()
            ));
        }
    }
}
