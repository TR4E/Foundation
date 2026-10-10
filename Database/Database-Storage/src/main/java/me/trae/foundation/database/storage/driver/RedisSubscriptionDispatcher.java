package me.trae.foundation.database.storage.driver;

import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class RedisSubscriptionDispatcher extends RedisPubSubAdapter<String, String> implements RedisConnectionStateListener {

    private final Map<String, List<Consumer<String>>> consumerMap = new ConcurrentHashMap<>();

    private volatile StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private volatile ExecutorService executorService;
    private volatile boolean disconnected;

    public synchronized void attach(final StatefulRedisPubSubConnection<String, String> pubSubConnection) {
        this.pubSubConnection = pubSubConnection;
        this.executorService = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("redis-subscription").factory());

        pubSubConnection.addListener((RedisPubSubListener<String, String>) this);
        pubSubConnection.addListener((RedisConnectionStateListener) this);

        if (!this.consumerMap.isEmpty()) {
            pubSubConnection.sync().subscribe(this.consumerMap.keySet().toArray(String[]::new));
        }

        if (this.disconnected) {
            this.notifyReconnect();
        }
    }

    public synchronized void detach() {
        this.disconnected = true;

        if (this.executorService != null) {
            this.executorService.shutdown();
            this.executorService = null;
        }

        this.pubSubConnection = null;
    }

    public synchronized void subscribe(final String channel, final Consumer<String> consumer) {
        final boolean newChannel = !this.consumerMap.containsKey(channel);

        this.consumerMap.computeIfAbsent(channel, _ -> new CopyOnWriteArrayList<>()).add(consumer);

        if (newChannel && this.pubSubConnection != null) {
            this.pubSubConnection.sync().subscribe(channel);
        }
    }

    public synchronized void unsubscribe(final String channel) {
        if (this.consumerMap.remove(channel) != null && this.pubSubConnection != null) {
            this.pubSubConnection.sync().unsubscribe(channel);
        }
    }

    @Override
    public void message(final String channel, final String message) {
        final ExecutorService current = this.executorService;
        if (current == null) {
            return;
        }

        current.execute(() -> this.consumerMap.getOrDefault(channel, Collections.emptyList()).forEach(consumer -> consumer.accept(message)));
    }

    @Override
    public void onRedisConnected(final RedisChannelHandler<?, ?> connection) {
        if (this.disconnected) {
            this.notifyReconnect();
        }
    }

    @Override
    public void onRedisConnected(final RedisChannelHandler<?, ?> connection, final java.net.SocketAddress socketAddress) {
        this.onRedisConnected(connection);
    }

    @Override
    public void onRedisDisconnected(final RedisChannelHandler<?, ?> connection) {
        this.disconnected = true;
    }

    @Override
    public void onRedisExceptionCaught(final RedisChannelHandler<?, ?> connection, final Throwable cause) {
    }

    private void notifyReconnect() {
        this.disconnected = false;
        final ExecutorService current = this.executorService;
        if (current == null) {
            return;
        }

        current.execute(this::notifySubscribers);
    }

    private void notifySubscribers() {
        for (final List<Consumer<String>> consumerList : this.consumerMap.values()) {
            for (final Consumer<String> consumer : consumerList) {
                consumer.accept("*");
            }
        }
    }
}