package me.trae.foundation.database.redis.driver;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import me.trae.foundation.database.api.exception.ConnectionException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class RedisSubscriptions extends RedisPubSubAdapter<String, String> {

    private final ConcurrentHashMap<String, Consumer<String>> listenerMap = new ConcurrentHashMap<>();

    private volatile StatefulRedisPubSubConnection<String, String> connection;

    public synchronized void attach(final StatefulRedisPubSubConnection<String, String> connection) {
        this.connection = connection;

        connection.addListener(this);

        if (!this.listenerMap.isEmpty()) {
            connection.sync().subscribe(this.listenerMap.keySet().toArray(String[]::new));
        }
    }

    public synchronized void detach() {
        if (this.connection != null) {
            this.connection.removeListener(this);
            this.connection = null;
        }
    }

    public void subscribe(final String channel, final Consumer<String> consumer) {
        this.listenerMap.put(channel, consumer);

        this.getConnection().sync().subscribe(channel);
    }

    public void unsubscribe(final String channel) {
        this.listenerMap.remove(channel);

        this.getConnection().sync().unsubscribe(channel);
    }

    @Override
    public void message(final String channel, final String message) {
        final Consumer<String> consumer = this.listenerMap.get(channel);

        if (consumer != null) {
            consumer.accept(message);
        }
    }

    private StatefulRedisPubSubConnection<String, String> getConnection() {
        final StatefulRedisPubSubConnection<String, String> current = this.connection;

        if (current == null) {
            throw new ConnectionException("Redis pub/sub is not connected");
        }

        return current;
    }
}