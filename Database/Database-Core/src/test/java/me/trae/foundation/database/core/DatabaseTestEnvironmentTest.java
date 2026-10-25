package me.trae.foundation.database.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.opentest4j.TestAbortedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class DatabaseTestEnvironmentTest {

    @Test
    void aListeningPortAllowsTheTestToContinue() throws IOException {
        try (final ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            assertDoesNotThrow(() -> DatabaseTestEnvironment.requirePort(
                    "test service",
                    serverSocket.getInetAddress().getHostAddress(),
                    serverSocket.getLocalPort()
            ));
        }
    }

    @Test
    void aClosedPortAbortsWithTheServiceAndEndpointInTheReason() throws IOException {
        final int port;
        final String host;

        try (final ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = serverSocket.getLocalPort();
            host = serverSocket.getInetAddress().getHostAddress();
        }

        final TestAbortedException testAbortedException = assertThrows(TestAbortedException.class,
                () -> DatabaseTestEnvironment.requirePort("PostgreSQL", host, port));
        assertTrue(testAbortedException.getMessage().contains("PostgreSQL"));
        assertTrue(testAbortedException.getMessage().contains("%s:%d".formatted(host, port)));
    }

    @Test
    void invalidPortConfigurationIsNotConvertedIntoASkip() {
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseTestEnvironment.requirePort("PostgreSQL", "localhost", -1));
    }

    @Test
    void aMissingDockerExecutableAborts() throws IOException {
        try (final MockedConstruction<ProcessBuilder> processBuilderMockedConstruction = mockConstruction(
                ProcessBuilder.class,
                (processBuilder, _) -> {
                    when(processBuilder.redirectErrorStream(true)).thenReturn(processBuilder);
                    when(processBuilder.start()).thenThrow(new IOException("Docker is not installed"));
                }
        )) {
            final TestAbortedException testAbortedException = assertThrows(TestAbortedException.class,
                    () -> DatabaseTestEnvironment.requireRunningDockerContainers("postgres-test", "redis-test"));
            assertTrue(testAbortedException.getMessage().contains("Docker CLI is unavailable"));
            verify(processBuilderMockedConstruction.constructed().getFirst()).start();
        }
    }

    @Test
    void anUnavailableDockerDaemonOrMissingContainerAborts() throws Exception {
        final Process process = mock(Process.class);
        when(process.waitFor(5L, TimeUnit.SECONDS)).thenReturn(true);
        when(process.exitValue()).thenReturn(1);

        try (final MockedConstruction<ProcessBuilder> processBuilderMockedConstruction = this.processBuilder(process)) {
            assertThrows(TestAbortedException.class,
                    () -> DatabaseTestEnvironment.requireRunningDockerContainers("postgres-test", "redis-test"));
            verify(processBuilderMockedConstruction.constructed().getFirst()).start();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"false\ntrue\n", "true\nfalse\n", "true\n", ""})
    void stoppedOrMissingContainersAbort(final String states) throws Exception {
        final Process process = this.completedProcess(states);

        try (final MockedConstruction<ProcessBuilder> processBuilderMockedConstruction = this.processBuilder(process)) {
            assertThrows(TestAbortedException.class,
                    () -> DatabaseTestEnvironment.requireRunningDockerContainers("postgres-test", "redis-test"));
            verify(processBuilderMockedConstruction.constructed().getFirst()).start();
        }
    }

    @Test
    void runningContainersAllowTheTestToContinue() throws Exception {
        final Process process = this.completedProcess("true\ntrue\n");

        try (final MockedConstruction<ProcessBuilder> processBuilderMockedConstruction = this.processBuilder(process)) {
            assertDoesNotThrow(() -> DatabaseTestEnvironment.requireRunningDockerContainers("postgres-test", "redis-test"));
            verify(processBuilderMockedConstruction.constructed().getFirst()).start();
        }
    }

    @Test
    void aDockerTimeoutAbortsAndDestroysTheProbeProcess() throws IOException {
        final Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(true);

        try (final MockedConstruction<ProcessBuilder> processBuilderMockedConstruction = this.processBuilder(process)) {
            assertThrows(TestAbortedException.class,
                    () -> DatabaseTestEnvironment.requireRunningDockerContainers("postgres-test", "redis-test"));
            verify(process).destroyForcibly();
            verify(processBuilderMockedConstruction.constructed().getFirst()).start();
        }
    }

    @Test
    void anInterruptedDockerProbePropagatesAndDestroysTheProcess() throws Exception {
        final Process process = mock(Process.class);
        when(process.waitFor(5L, TimeUnit.SECONDS)).thenThrow(new InterruptedException("interrupted"));
        when(process.isAlive()).thenReturn(true);

        try (final MockedConstruction<ProcessBuilder> processBuilderMockedConstruction = this.processBuilder(process)) {
            assertThrows(InterruptedException.class,
                    () -> DatabaseTestEnvironment.requireRunningDockerContainers("postgres-test", "redis-test"));
            verify(process).destroyForcibly();
            verify(processBuilderMockedConstruction.constructed().getFirst()).start();
        }
    }

    private Process completedProcess(final String states) throws InterruptedException {
        final Process process = mock(Process.class);
        when(process.waitFor(5L, TimeUnit.SECONDS)).thenReturn(true);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(states.getBytes(StandardCharsets.UTF_8)));
        return process;
    }

    private MockedConstruction<ProcessBuilder> processBuilder(final Process process) {
        return mockConstruction(ProcessBuilder.class, (processBuilder, _) -> {
            when(processBuilder.redirectErrorStream(true)).thenReturn(processBuilder);
            when(processBuilder.start()).thenReturn(process);
        });
    }
}
