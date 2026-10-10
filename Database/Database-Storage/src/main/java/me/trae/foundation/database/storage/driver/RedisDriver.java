package me.trae.foundation.database.storage.driver;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.database.api.exception.ConnectionException;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class RedisDriver {

    private final RedisSubscriptionDispatcher subscriptionDispatcher = new RedisSubscriptionDispatcher();

    private final String instanceId = UUID.randomUUID().toString();

    @Getter
    private final RedisSettings redisSettings;

    private RedisClient redisClient;

    private volatile StatefulRedisConnection<String, String> connection;

    private StatefulRedisPubSubConnection<String, String> pubSubConnection;

    public synchronized void connect() {
        if (this.isConnected()) {
            return;
        }

        try {
            this.redisClient = RedisClient.create(this.createRedisURI());
            this.connection = this.redisClient.connect();
            this.pubSubConnection = this.redisClient.connectPubSub();

            this.subscriptionDispatcher.attach(this.pubSubConnection);
        } catch (final RedisException exception) {
            this.disconnect();
            throw new ConnectionException("Failed to connect to Redis at %s:%s".formatted(this.redisSettings.getHost(), this.redisSettings.getPort()), exception);
        }
    }

    public synchronized void disconnect() {
        this.subscriptionDispatcher.detach();

        if (this.pubSubConnection != null) {
            this.pubSubConnection.close();
            this.pubSubConnection = null;
        }

        if (this.connection != null) {
            this.connection.close();
            this.connection = null;
        }

        if (this.redisClient != null) {
            this.redisClient.shutdown();
            this.redisClient = null;
        }
    }

    public boolean isConnected() {
        final StatefulRedisConnection<String, String> current = this.connection;

        return current != null && current.isOpen();
    }

    public RedisCommands<String, String> getCommands() {
        return this.getConnection().sync();
    }

    public RedisAsyncCommands<String, String> getAsyncCommands() {
        return this.getConnection().async();
    }

    public void publish(final String channel, final String message) {
        this.getCommands().publish(channel, message);
    }

    public void subscribe(final String channel, final Consumer<String> consumer) {
        this.subscriptionDispatcher.subscribe(channel, consumer);
    }

    public void unsubscribe(final String channel) {
        this.subscriptionDispatcher.unsubscribe(channel);
    }

    public String getInstanceId() {
        return this.instanceId;
    }

    public boolean tryAcquireLock(final String key, final String owner, final Duration lease) {
        return "OK".equals(this.getCommands().set(key, owner, SetArgs.Builder.nx().px(lease.toMillis())));
    }

    public void releaseLock(final String key, final String owner) {
        this.getCommands().eval("if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end", ScriptOutputType.INTEGER, new String[]{key}, owner);
    }

    private StatefulRedisConnection<String, String> getConnection() {
        final StatefulRedisConnection<String, String> current = this.connection;

        if (current == null || !current.isOpen()) {
            throw new ConnectionException("Redis is not connected");
        }

        return current;
    }

    private RedisURI createRedisURI() {
        final RedisURI.Builder builder = RedisURI.builder()
                .withHost(this.redisSettings.getHost())
                .withPort(this.redisSettings.getPort())
                .withDatabase(this.redisSettings.getDatabase())
                .withTimeout(this.redisSettings.getTimeout());

        if (this.redisSettings.getPassword() != null && !this.redisSettings.getPassword().isEmpty()) {
            builder.withPassword(this.redisSettings.getPassword().toCharArray());
        }

        return builder.build();
    }
}