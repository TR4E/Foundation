package me.trae.foundation.database.redis.driver;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.Delay;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.database.api.exception.ConnectionException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@CustomLog
@RequiredArgsConstructor
public final class RedisDriver {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RECONNECT_MINIMUM = Duration.ofMillis(20);
    private static final Duration RECONNECT_MAXIMUM = Duration.ofMillis(500);

    private static final int POOL = Math.max(4, Runtime.getRuntime().availableProcessors());

    private final RedisSubscriptions subscriptions = new RedisSubscriptions();
    private final List<StatefulRedisConnection<String, String>> connections = new ArrayList<>();
    private final AtomicInteger cursor = new AtomicInteger();

    @Getter
    private final String instanceId = UUID.randomUUID().toString();

    @Getter
    private final RedisSettings redisSettings;

    private RedisClient redisClient;

    private volatile ClientResources clientResources;
    private volatile StatefulRedisConnection<String, String> batchConnection;

    private StatefulRedisPubSubConnection<String, String> pubSubConnection;

    public synchronized void connect() {
        if (this.isConnected()) {
            return;
        }

        try {
            this.clientResources = resources();

            this.redisClient = RedisClient.create(this.clientResources, this.createRedisURI());

            this.redisClient.setOptions(options());

            for (int index = 0; index < POOL; index++) {
                this.connections.add(this.redisClient.connect());
            }

            this.batchConnection = this.redisClient.connect();
            this.pubSubConnection = this.redisClient.connectPubSub();

            this.subscriptions.attach(this.pubSubConnection);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Connected to Redis at {}:{} with {} connections", this.redisSettings.getHost(), this.redisSettings.getPort(), POOL);
            }
        } catch (final RedisException exception) {
            this.disconnect();
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Failed to connect to Redis at {}:{}", this.redisSettings.getHost(), this.redisSettings.getPort(), exception);
            }
            throw new ConnectionException("Failed to connect to Redis at %s:%s".formatted(this.redisSettings.getHost(), this.redisSettings.getPort()), exception);
        }
    }

    public synchronized void disconnect() {
        this.subscriptions.detach();

        if (this.pubSubConnection != null) {
            this.pubSubConnection.close();

            this.pubSubConnection = null;
        }

        if (this.batchConnection != null) {
            this.batchConnection.close();

            this.batchConnection = null;
        }

        this.connections.forEach(StatefulRedisConnection::close);
        this.connections.clear();

        if (this.redisClient != null) {
            this.redisClient.shutdown();

            this.redisClient = null;
        }

        if (this.clientResources != null) {
            this.clientResources.shutdown();

            this.clientResources = null;
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Disconnected from Redis at {}:{}", this.redisSettings.getHost(), this.redisSettings.getPort());
            }
        }
    }

    public boolean isConnected() {
        return this.connections.stream().anyMatch(StatefulRedisConnection::isOpen);
    }

    public RedisCommands<String, String> getCommands() {
        return this.getConnection().sync();
    }

    public RedisAsyncCommands<String, String> getAsyncCommands() {
        return this.getConnection().async();
    }

    public List<Map<String, String>> hashes(final Collection<String> paths) {
        if (paths.isEmpty()) {
            return Collections.emptyList();
        }

        final StatefulRedisConnection<String, String> current = this.getBatchConnection();

        final RedisAsyncCommands<String, String> commands = current.async();

        final List<RedisFuture<Map<String, String>>> futures = new ArrayList<>(paths.size());

        current.setAutoFlushCommands(false);

        try {
            paths.forEach(path -> futures.add(commands.hgetall(path)));

            current.flushCommands();
        } finally {
            current.setAutoFlushCommands(true);
        }

        return futures.stream()
                .map(future -> future.toCompletableFuture().join())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<String> fields(final Collection<String> paths, final String field) {
        if (paths.isEmpty()) {
            return Collections.emptyList();
        }

        final StatefulRedisConnection<String, String> current = this.getBatchConnection();

        final RedisAsyncCommands<String, String> commands = current.async();

        final List<RedisFuture<String>> futures = new ArrayList<>(paths.size());

        current.setAutoFlushCommands(false);

        try {
            paths.forEach(path -> futures.add(commands.hget(path, field)));

            current.flushCommands();
        } finally {
            current.setAutoFlushCommands(true);
        }

        return futures.stream()
                .map(future -> future.toCompletableFuture().join())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public void set(final Map<String, String> values, final Duration ttl) {
        if (values.isEmpty()) {
            return;
        }

        final StatefulRedisConnection<String, String> current = this.getBatchConnection();

        final RedisAsyncCommands<String, String> commands = current.async();

        RedisFuture<String> last = null;

        current.setAutoFlushCommands(false);

        try {
            for (final Map.Entry<String, String> entry : values.entrySet()) {
                last = ttl.isZero() ? commands.set(entry.getKey(), entry.getValue()) : commands.set(entry.getKey(), entry.getValue(), SetArgs.Builder.px(ttl.toMillis()));
            }

            current.flushCommands();
        } finally {
            current.setAutoFlushCommands(true);
        }

        if (last != null) {
            last.toCompletableFuture().join();
        }
    }

    public void expire(final Collection<String> paths, final Duration ttl) {
        if (paths.isEmpty() || ttl.isZero()) {
            return;
        }

        final StatefulRedisConnection<String, String> current = this.getBatchConnection();

        final RedisAsyncCommands<String, String> commands = current.async();

        RedisFuture<Boolean> last = null;

        current.setAutoFlushCommands(false);

        try {
            for (final String path : paths) {
                last = commands.pexpire(path, ttl.toMillis());
            }

            current.flushCommands();
        } finally {
            current.setAutoFlushCommands(true);
        }

        if (last != null) {
            last.toCompletableFuture().join();
        }
    }

    public void subscribe(final Map<String, Consumer<String>> listeners) {
        this.subscriptions.subscribe(listeners);
    }

    public void unsubscribe(final String channel) {
        this.subscriptions.unsubscribe(channel);
    }

    private StatefulRedisConnection<String, String> getConnection() {
        if (this.connections.isEmpty()) {
            throw new ConnectionException("Redis is not connected");
        }

        final int size = this.connections.size();

        final int start = Math.floorMod(this.cursor.getAndIncrement(), size);

        for (int offset = 0; offset < size; offset++) {
            final StatefulRedisConnection<String, String> current = this.connections.get((start + offset) % size);

            if (current.isOpen()) {
                return current;
            }
        }

        throw new ConnectionException("Redis is not connected");
    }

    private StatefulRedisConnection<String, String> getBatchConnection() {
        final StatefulRedisConnection<String, String> current = this.batchConnection;

        if (current == null || !current.isOpen()) {
            throw new ConnectionException("Redis is not connected");
        }

        return current;
    }

    private static ClientResources resources() {
        return ClientResources.builder()
                .reconnectDelay(Delay.exponential(RECONNECT_MINIMUM.toMillis(), RECONNECT_MAXIMUM.toMillis(), TimeUnit.MILLISECONDS, 2))
                .build();
    }

    private static ClientOptions options() {
        return ClientOptions.builder()
                .autoReconnect(true)
                .socketOptions(SocketOptions.builder().connectTimeout(CONNECT_TIMEOUT).keepAlive(true).build())
                .timeoutOptions(TimeoutOptions.enabled(COMMAND_TIMEOUT))
                .build();
    }

    private RedisURI createRedisURI() {
        final RedisURI.Builder builder = RedisURI.builder()
                .withHost(this.redisSettings.getHost())
                .withPort(this.redisSettings.getPort())
                .withDatabase(this.redisSettings.getDatabase())
                .withTimeout(this.redisSettings.getTimeout());

        if (this.redisSettings.getUsername() != null && !this.redisSettings.getUsername().isEmpty()) {
            builder.withAuthentication(this.redisSettings.getUsername(), this.redisSettings.getPassword() == null ? new char[0] : this.redisSettings.getPassword().toCharArray());
        } else if (this.redisSettings.getPassword() != null && !this.redisSettings.getPassword().isEmpty()) {
            builder.withPassword(this.redisSettings.getPassword().toCharArray());
        }

        return builder.build();
    }
}