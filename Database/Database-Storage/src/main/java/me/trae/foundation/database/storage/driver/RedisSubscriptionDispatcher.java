package me.trae.foundation.database.storage.driver;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class RedisSubscriptionDispatcher extends RedisPubSubAdapter<String, String> {

    private final Map<String, List<Consumer<String>>> consumerMap = new ConcurrentHashMap<>();

    private volatile StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private volatile ExecutorService executorService;

    public synchronized void attach(final StatefulRedisPubSubConnection<String, String> pubSubConnection) {
        this.pubSubConnection = pubSubConnection;
        this.executorService = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("redis-subscription").factory());

        pubSubConnection.addListener(this);

        if (!this.consumerMap.isEmpty()) {
            pubSubConnection.sync().subscribe(this.consumerMap.keySet().toArray(String[]::new));
        }
    }

    public synchronized void detach() {
        if (this.executorService != null) {
            this.executorService.shutdown();
            this.executorService = null;
        }

        this.pubSubConnection = null;
    }

    public synchronized void subscribe(final String channel, final Consumer<String> consumer) {
        final boolean newChannel = !this.consumerMap.containsKey(channel);

        this.consumerMap.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>()).add(consumer);

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
}