package me.trae.foundation.database.storage.redis;

import io.lettuce.core.Limit;
import io.lettuce.core.Range;
import io.lettuce.core.ScriptOutputType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.exception.SynchronizationException;
import me.trae.foundation.database.api.utility.DatabaseCallbacks;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Tracks cache owners and acknowledges completed refreshes, independently of the database worker.
 */
@RequiredArgsConstructor
public final class RedisEntityCoordinator {

    private static final System.Logger LOGGER = System.getLogger(RedisEntityCoordinator.class.getName());

    private static final String PREFIX = "foundation:entity-sync:";

    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(5);

    private static final RedisScript HEARTBEAT_SCRIPT = new RedisScript("""
            local clock = redis.call('TIME')
            local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
            redis.call('PSETEX', KEYS[1], 30000, '1')
            redis.call('ZADD', KEYS[2], now + 30000, ARGV[1])
            return now
            """);

    private static final RedisScript REGISTER_SCRIPT = new RedisScript("""
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            redis.call('SADD', KEYS[2], KEYS[1] .. '|' .. ARGV[1] .. '|' .. ARGV[2])
            return redis.call('GET', KEYS[3]) or '0'
            """);

    private static final RedisScript REMOVE_SCRIPT = new RedisScript("""
            if redis.call('HGET', KEYS[1], ARGV[1]) == ARGV[2] then
                redis.call('HDEL', KEYS[1], ARGV[1])
                if redis.call('HLEN', KEYS[1]) == 0 then redis.call('DEL', KEYS[1]) end
            end
            redis.call('SREM', KEYS[2], KEYS[1] .. '|' .. ARGV[1] .. '|' .. ARGV[2])
            return 1
            """);

    private static final RedisScript NOTIFY_SCRIPT = new RedisScript("""
            if ARGV[1] == '1' then redis.call('DEL', KEYS[1], KEYS[6]) end
            redis.call('DEL', KEYS[2])
            if ARGV[2] == '1' then redis.call('DEL', KEYS[3]) end
            for index = 10, #ARGV, 2 do
                if ARGV[9] == '1' then
                    redis.call('HSETNX', KEYS[3], ARGV[index], ARGV[index + 1])
                else
                    redis.call('HSET', KEYS[3], ARGV[index], ARGV[index + 1])
                end
            end
            local generation = redis.call('INCR', KEYS[5])
            local owners = redis.call('HGETALL', KEYS[4])
            local result = {}
            local groups = {}
            for index = 1, #owners, 2 do
                local holder = owners[index]
                local registration = owners[index + 1]
                local session = string.sub(registration, 1, 36)
                if redis.call('EXISTS', ARGV[3] .. 'lease:' .. session) == 1 then
                    if holder ~= ARGV[6] then
                        table.insert(result, holder)
                        if not groups[session] then groups[session] = {} end
                        table.insert(groups[session], holder)
                    end
                else
                    redis.call('HDEL', KEYS[4], holder)
                end
            end
            local recipients = { tostring(generation) }
            for session, holders in pairs(groups) do
                for _, holder in ipairs(holders) do
                    table.insert(recipients, holder .. ':' .. session)
                end
            end
            return recipients
            """);

    private static final RedisScript CLEAN_SCRIPT = new RedisScript("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            local entries = redis.call('SPOP', KEYS[2], 100)
            for _, entry in ipairs(entries) do
                local key, holder, registration = string.match(entry, '^(.*)|([^|]+)|([^|]+)$')
                if key and redis.call('HGET', key, holder) == registration then
                    redis.call('HDEL', key, holder)
                    if redis.call('HLEN', key) == 0 then redis.call('DEL', key) end
                end
            end
            if redis.call('SCARD', KEYS[2]) == 0 then redis.call('ZREM', KEYS[3], ARGV[1]) end
            return #entries
            """);

    private static final long[] REPAIR_DELAY_ARRAY = {100L, 250L, 500L, 1_000L, 2_000L, 5_000L};

    private static final long REPAIR_WARNING_INTERVAL_NANOS = Duration.ofSeconds(30).toNanos();

    private static final int MAXIMUM_PENDING = 100_000;

    private final ConcurrentHashMap<String, Binding> bindingMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Admission> admissionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingAcknowledgements> pendingMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<String>> deliveryMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Repair> repairMap = new ConcurrentHashMap<>();

    private final ThreadLocal<Boolean> recoveryThread = ThreadLocal.withInitial(() -> false);

    private final String sessionId = UUID.randomUUID().toString();

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    private final RedisDriver redisDriver;

    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("entity-sync-maintenance").factory());
    private volatile long leaseDeadlineNanos;
    private volatile boolean started;
    private volatile boolean closed;
    private volatile boolean revalidating;

    public void bind(final String namespace, final String holderId, final Consumer<UUID> refreshConsumer, final Runnable reconnectRunnable, final Consumer<Throwable> failureConsumer) {
        this.bindingMap.put(holderId, new Binding(namespace, refreshConsumer, reconnectRunnable, failureConsumer));
    }

    public void unbind(final String holderId) {
        this.admissionMap.values().stream().filter(admission -> admission.getHolderId().equals(holderId)).toList()
                .forEach(admission -> this.remove(admission.getNamespace(), admission.getId(), holderId));
        this.bindingMap.remove(holderId);
    }

    public Admission admit(final String namespace, final UUID id, final String holderId) {
        this.start();
        this.checkLease();

        final String key = "%s|%s|%s".formatted(namespace, id, holderId);
        final Admission cachedAdmission = this.admissionMap.get(key);
        if (cachedAdmission != null) {
            return cachedAdmission;
        }

        final Admission admission = new Admission(namespace, id, holderId, "%s:%s".formatted(this.sessionId, UUID.randomUUID()));
        final Admission existing = this.admissionMap.putIfAbsent(key, admission);
        if (existing != null) {
            return existing;
        }

        try {
            admission.generation = Long.parseLong(REGISTER_SCRIPT.<String>execute(
                    this.redisDriver,
                    ScriptOutputType.VALUE,
                    new String[]{this.ownerKey(namespace, id), this.membershipKey(this.sessionId), this.generationKey(namespace, id)},
                    holderId,
                    admission.getRegistration()
            ));
            return admission;
        } catch (final RuntimeException | Error exception) {
            this.admissionMap.remove(key, admission);
            admission.getReadyFuture().completeExceptionally(exception);
            throw exception;
        }
    }

    public void remove(final String namespace, final UUID id, final String holderId) {
        final Admission admission = this.admissionMap.remove("%s|%s|%s".formatted(namespace, id, holderId));
        if (admission == null) {
            return;
        }

        admission.getReadyFuture().complete(null);
        final Repair repair = this.repairMap.remove("%s|%s|%s".formatted(namespace, id, holderId));
        if (repair != null && repair.getScheduledFuture() != null) {
            repair.getScheduledFuture().cancel(false);
        }
        this.executorService.execute(() -> this.removeRegistration(admission));
    }

    private void removeRegistration(final Admission admission) {
        try {
            REMOVE_SCRIPT.execute(
                    this.redisDriver,
                    ScriptOutputType.INTEGER,
                    new String[]{this.ownerKey(admission.getNamespace(), admission.getId()), this.membershipKey(admission.getRegistration().substring(0, 36))},
                    admission.getHolderId(),
                    admission.getRegistration()
            );
        } catch (final RuntimeException | Error exception) {
            this.report(admission.getHolderId(), exception);
        }
    }

    public long generation(final String namespace, final UUID id) {
        final String value = this.redisDriver.getCommands().get(this.generationKey(namespace, id));
        return value == null ? 0L : Long.parseLong(value);
    }

    public CompletableFuture<Void> notifyOwners(final RedisInvalidation redisInvalidation, final UUID notificationId, final boolean wait, final Duration timeout) {
        this.start();

        final CompletableFuture<Void> resultFuture = new CompletableFuture<>();
        this.executorService.execute(() -> {
            try {
                this.dispatch(redisInvalidation, notificationId, wait, Map.of(), false, timeout).whenComplete((_, exception) -> {
                    if (exception == null) {
                        resultFuture.complete(null);
                    } else {
                        resultFuture.completeExceptionally(exception);
                    }
                });
            } catch (final RuntimeException | Error exception) {
                resultFuture.completeExceptionally(exception);
            }
        });
        return resultFuture;
    }

    public CompletableFuture<Void> notifyOwners(final RedisInvalidation redisInvalidation, final UUID notificationId, final boolean wait) {
        return this.notifyOwners(redisInvalidation, notificationId, wait, ACK_TIMEOUT);
    }

    public CompletableFuture<Void> notifyOwnersNow(final RedisInvalidation redisInvalidation, final UUID notificationId, final boolean wait, final Map<String, String> sharedValueMap, final boolean initialize, final Duration timeout) {
        this.start();
        return this.dispatch(redisInvalidation, notificationId, wait, sharedValueMap, initialize, timeout);
    }

    public CompletableFuture<Void> notifyOwnersNow(final RedisInvalidation redisInvalidation, final UUID notificationId, final boolean wait, final Map<String, String> sharedValueMap, final boolean initialize) {
        return this.notifyOwnersNow(redisInvalidation, notificationId, wait, sharedValueMap, initialize, ACK_TIMEOUT);
    }

    public CompletableFuture<Void> notifyOwnersNow(final RedisInvalidation redisInvalidation, final UUID notificationId, final boolean wait, final Map<String, String> sharedValueMap) {
        return this.notifyOwnersNow(redisInvalidation, notificationId, wait, sharedValueMap, false);
    }

    public CompletableFuture<Void> notifyOwnersNow(final RedisInvalidation redisInvalidation, final UUID notificationId, final boolean wait) {
        return this.notifyOwnersNow(redisInvalidation, notificationId, wait, Map.of());
    }

    private CompletableFuture<Void> dispatch(final RedisInvalidation redisInvalidation, final UUID notificationId, final boolean wait, final Map<String, String> sharedValueMap, final boolean initialize, final Duration timeout) {
        final String notification = notificationId.toString();
        final PendingAcknowledgements pendingAcknowledgements = wait ? new PendingAcknowledgements() : null;
        if (wait) {
            if (this.pendingMap.size() >= MAXIMUM_PENDING) {
                throw new SynchronizationException("Too many cache synchronization requests are pending");
            }

            final PendingAcknowledgements existing = this.pendingMap.putIfAbsent(notification, pendingAcknowledgements);
            if (existing != null) {
                return existing.getFuture();
            }
        }

        final AtomicBoolean writeCommitted = new AtomicBoolean();
        try {
            final String cacheKey = redisInvalidation.getCacheKey();
            final String namespace = cacheKey.substring(0, cacheKey.length() - redisInvalidation.getEntityId().toString().length());
            final List<String> argumentList = new ArrayList<>(List.of(
                    redisInvalidation.isInvalidateCache() ? "1" : "0",
                    redisInvalidation.isRemoveSharedState() ? "1" : "0",
                    PREFIX,
                    notification,
                    wait ? this.sessionId : "-",
                    redisInvalidation.getInstanceId(),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(namespace.getBytes(StandardCharsets.UTF_8)),
                    redisInvalidation.getEntityId().toString(),
                    initialize ? "1" : "0"
            ));
            sharedValueMap.forEach((field, value) -> {
                argumentList.add(field);
                argumentList.add(value);
            });
            final List<String> receiptList = NOTIFY_SCRIPT.execute(
                    this.redisDriver,
                    ScriptOutputType.MULTI,
                    new String[]{
                            cacheKey,
                            redisInvalidation.getMissingKey(),
                            "%scache:shared:%s".formatted(namespace, redisInvalidation.getEntityId()),
                            this.ownerKey(namespace, redisInvalidation.getEntityId()),
                            this.generationKey(namespace, redisInvalidation.getEntityId()),
                            redisInvalidation.getLockKey()
                    },
                    argumentList.toArray(String[]::new)
            );

            writeCommitted.set(true);
            final List<String> ownerList = new ArrayList<>();
            final Map<String, List<String>> groupMap = new HashMap<>();
            for (final String recipient : receiptList.subList(1, receiptList.size())) {
                final String[] parts = recipient.split(":", 2);
                ownerList.add(parts[0]);
                groupMap.computeIfAbsent(parts[1], _ -> new ArrayList<>()).add(parts[0]);
            }
            groupMap.forEach((session, holders) -> this.redisDriver.publish(
                    "%snotify:%s".formatted(PREFIX, session),
                    "%s|%s|%s|%s|%s|%s|%s".formatted(notification, wait ? this.sessionId : "-", argumentList.get(6), redisInvalidation.getEntityId(), redisInvalidation.getInstanceId(), String.join(",", holders), receiptList.getFirst())
            ));
            if (!wait) {
                return CompletableFuture.completedFuture(null);
            }

            pendingAcknowledgements.expect(ownerList);
            if (!pendingAcknowledgements.getFuture().isDone()) {
                final ScheduledFuture<?> timeoutFuture = this.scheduledExecutorService.schedule(pendingAcknowledgements::timeout, timeout.toNanos(), TimeUnit.NANOSECONDS);
                pendingAcknowledgements.getFuture().whenComplete((_, _) -> timeoutFuture.cancel(false));
            }
            pendingAcknowledgements.getFuture().whenComplete((_, _) -> this.pendingMap.remove(notification, pendingAcknowledgements));
            return pendingAcknowledgements.getFuture();
        } catch (final RuntimeException | Error exception) {
            if (wait) {
                this.pendingMap.remove(notification, pendingAcknowledgements);
                pendingAcknowledgements.getFuture().completeExceptionally(exception);
            }
            if (writeCommitted.get()) {
                return CompletableFuture.failedFuture(new SynchronizationException("Write committed but owner publication failed", exception));
            }
            throw exception;
        }
    }

    private synchronized void start() {
        if (this.closed) {
            throw new ConnectionException("Cache synchronization coordinator has stopped");
        }

        if (this.started) {
            return;
        }

        if (this.scheduledExecutorService.isShutdown()) {
            this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("entity-sync-maintenance").factory());
        }

        this.redisDriver.subscribe("%snotify:%s".formatted(PREFIX, this.sessionId), this::receive);
        this.redisDriver.subscribe("%sack:%s".formatted(PREFIX, this.sessionId), this::acknowledge);
        this.heartbeat();
        this.scheduledExecutorService.scheduleWithFixedDelay(this::maintain, 10L, 10L, TimeUnit.SECONDS);
        this.started = true;
    }

    private void receive(final String message) {
        if (message.equals("*")) {
            return;
        }

        final String[] partArray = message.split("\\|", -1);
        if (partArray.length != 7) {
            return;
        }

        final List<CompletableFuture<String>> acknowledgementList = new ArrayList<>();
        for (final String holderId : partArray[5].split(",")) {
            final String[] deliveryPartArray = partArray.clone();
            deliveryPartArray[5] = holderId;
            acknowledgementList.add(this.deliver(deliveryPartArray).thenApply(status -> "%s:%s".formatted(holderId, status)));
        }

        if (!partArray[1].equals("-")) {
            CompletableFuture.allOf(acknowledgementList.toArray(CompletableFuture[]::new)).thenRun(() -> this.redisDriver.getAsyncCommands().publish(
                    "%sack:%s".formatted(PREFIX, partArray[1]),
                    "%s|%s".formatted(partArray[0], String.join(",", acknowledgementList.stream().map(CompletableFuture::join).toList()))
            ));
        }
    }

    private CompletableFuture<String> deliver(final String[] partArray) {
        final String key = "%s|%s".formatted(partArray[0], partArray[5]);
        final CompletableFuture<String> future = new CompletableFuture<>();
        final CompletableFuture<String> existing = this.deliveryMap.putIfAbsent(key, future);
        final CompletableFuture<String> deliveryFuture = existing == null ? future : existing;
        if (existing == null) {
            this.executorService.execute(() -> {
                try {
                    final Binding binding = this.bindingMap.get(partArray[5]);
                    if (binding != null) {
                        final String namespace = new String(Base64.getUrlDecoder().decode(partArray[2]), StandardCharsets.UTF_8);
                        if (!binding.getNamespace().equals(namespace)) {
                            throw new IllegalStateException("Notification namespace does not match its cache owner");
                        }
                        final UUID id = UUID.fromString(partArray[3]);
                        final Admission admission = this.admissionMap.get("%s|%s|%s".formatted(namespace, id, partArray[5]));
                        if (admission != null) {
                            admission.getReadyFuture().join();
                            binding.getRefreshConsumer().accept(id);
                        }
                    }

                    future.complete("1");
                } catch (final RuntimeException | Error exception) {
                    future.complete("0");
                    this.deliveryMap.remove(key, future);
                    this.repair(partArray, exception);
                    this.report(partArray[5], exception);
                }
            });
            this.scheduledExecutorService.schedule(() -> this.deliveryMap.remove(key, future), 30L, TimeUnit.SECONDS);
        }

        return deliveryFuture;
    }

    private void repair(final String[] parts, final Throwable exception) {
        if (this.closed) {
            return;
        }
        final String namespace = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
        final UUID id = UUID.fromString(parts[3]);
        final String key = "%s|%s|%s".formatted(namespace, id, parts[5]);
        final Repair repair = new Repair(namespace, id, parts[5]);
        if (this.repairMap.putIfAbsent(key, repair) == null) {
            LOGGER.log(
                    System.Logger.Level.ERROR,
                    "Entity cache refresh failed; serving the last cached value while repair retries: namespace=%s entity=%s holder=%s session=%s".formatted(namespace, id, parts[5], this.sessionId),
                    exception
            );
            this.scheduleRepair(key, repair);
        }
    }

    private void scheduleRepair(final String key, final Repair repair) {
        if (this.closed || this.scheduledExecutorService.isShutdown()) {
            return;
        }
        final long delay = REPAIR_DELAY_ARRAY[Math.min(repair.getAttempt().getAndIncrement(), REPAIR_DELAY_ARRAY.length - 1)];
        repair.setScheduledFuture(this.scheduledExecutorService.schedule(() -> this.executorService.execute(() -> this.runRepair(key, repair)), delay, TimeUnit.MILLISECONDS));
    }

    private void runRepair(final String key, final Repair repair) {
        final Binding binding = this.bindingMap.get(repair.getHolderId());
        final Admission admission = this.admissionMap.get(key);
        if (this.closed || binding == null || admission == null) {
            this.repairMap.remove(key, repair);
            return;
        }
        try {
            admission.getReadyFuture().join();
            final long before = this.generation(repair.getNamespace(), repair.getId());
            binding.getRefreshConsumer().accept(repair.getId());
            if (before == this.generation(repair.getNamespace(), repair.getId())) {
                if (this.repairMap.remove(key, repair)) {
                    LOGGER.log(System.Logger.Level.INFO, "Entity cache repair recovered: namespace=%s entity=%s holder=%s session=%s elapsedMs=%s retryCount=%s".formatted(
                            repair.getNamespace(),
                            repair.getId(),
                            repair.getHolderId(),
                            this.sessionId,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - repair.getStartedNanos()),
                            repair.getAttempt().get()
                    ));
                }
                return;
            }
            this.warnRepair(repair, null);
        } catch (final RuntimeException | Error exception) {
            this.warnRepair(repair, exception);
        }
        this.scheduleRepair(key, repair);
    }

    private void warnRepair(final Repair repair, final Throwable exception) {
        final long now = System.nanoTime();
        final long previous = repair.getLastWarningNanos().get();
        if (now - previous >= REPAIR_WARNING_INTERVAL_NANOS && repair.getLastWarningNanos().compareAndSet(previous, now)) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Entity cache repair is still pending; cached reads may be stale: namespace=%s entity=%s holder=%s session=%s elapsedMs=%s retryCount=%s".formatted(
                            repair.getNamespace(),
                            repair.getId(),
                            repair.getHolderId(),
                            this.sessionId,
                            TimeUnit.NANOSECONDS.toMillis(now - repair.getStartedNanos()),
                            repair.getAttempt().get()
                    ),
                    exception
            );
        }
    }

    private void acknowledge(final String message) {
        final String[] partArray = message.split("\\|", -1);
        if (partArray.length == 2) {
            final PendingAcknowledgements pendingAcknowledgements = this.pendingMap.get(partArray[0]);
            if (pendingAcknowledgements != null) {
                for (final String acknowledgement : partArray[1].split(",")) {
                    final String[] resultArray = acknowledgement.split(":", 2);
                    if (resultArray.length == 2) {
                        pendingAcknowledgements.acknowledge(resultArray[0], resultArray[1].equals("1"));
                    }
                }
            }
        }
    }

    private long heartbeat() {
        final long startedNanos = System.nanoTime();
        final long serverMillis = HEARTBEAT_SCRIPT.execute(
                this.redisDriver,
                ScriptOutputType.INTEGER,
                new String[]{"%slease:%s".formatted(PREFIX, this.sessionId), "%ssessions".formatted(PREFIX)},
                this.sessionId
        );
        this.leaseDeadlineNanos = startedNanos + Duration.ofSeconds(30).toNanos();
        return serverMillis;
    }

    private void maintain() {
        if (!this.redisDriver.isConnected()) {
            return;
        }

        if (System.nanoTime() >= this.leaseDeadlineNanos) {
            this.reconnect();
            return;
        }

        try {
            final long serverMillis = this.heartbeat();
            final List<String> expiredSessionList = this.redisDriver.getCommands().zrangebyscore(
                    "%ssessions".formatted(PREFIX),
                    Range.create(0L, serverMillis),
                    Limit.create(0L, 10L)
            );

            for (final String expiredSession : expiredSessionList) {
                CLEAN_SCRIPT.execute(
                        this.redisDriver,
                        ScriptOutputType.INTEGER,
                        new String[]{"%slease:%s".formatted(PREFIX, expiredSession), this.membershipKey(expiredSession), "%ssessions".formatted(PREFIX)},
                        expiredSession
                );
            }
        } catch (final RuntimeException | Error exception) {
            this.bindingMap.keySet().forEach(holder -> this.report(holder, exception));
        }
    }

    public void checkLease() {
        if (this.started && !this.recoveryThread.get() && (this.revalidating || System.nanoTime() >= this.leaseDeadlineNanos)) {
            throw new ConnectionException("Cached shared entities must be revalidated after their owner lease expires");
        }
    }

    public synchronized void reconnect() {
        if (!this.started || this.closed) {
            return;
        }

        if (this.revalidating) {
            return;
        }

        this.revalidating = true;
        this.leaseDeadlineNanos = 0L;
        if (this.scheduledExecutorService.isShutdown()) {
            this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("entity-sync-maintenance").factory());
            this.scheduledExecutorService.scheduleWithFixedDelay(this::maintain, 10L, 10L, TimeUnit.SECONDS);
        }

        this.executorService.execute(() -> {
            this.recoveryThread.set(true);
            try {
                this.heartbeat();
                this.admissionMap.values().forEach(admission -> REGISTER_SCRIPT.execute(
                        this.redisDriver,
                        ScriptOutputType.VALUE,
                        new String[]{this.ownerKey(admission.getNamespace(), admission.getId()), this.membershipKey(this.sessionId), this.generationKey(admission.getNamespace(), admission.getId())},
                        admission.getHolderId(),
                        admission.getRegistration()
                ));
                this.bindingMap.values().forEach(binding -> binding.getReconnectRunnable().run());
                this.revalidating = false;
                this.repairMap.forEach((key, repair) -> {
                    if (repair.getScheduledFuture() == null || repair.getScheduledFuture().isDone()) {
                        this.scheduleRepair(key, repair);
                    }
                });
            } catch (final RuntimeException | Error exception) {
                this.leaseDeadlineNanos = 0L;
                this.revalidating = false;
                this.bindingMap.keySet().forEach(holder -> this.report(holder, exception));
            } finally {
                this.recoveryThread.remove();
            }
        });
    }

    public void connectionLost() {
        this.leaseDeadlineNanos = 0L;
        this.pendingMap.values().forEach(pending -> pending.getFuture().completeExceptionally(new SynchronizationException("Redis disconnected before cache synchronization completed")));
    }

    public void suspend() {
        this.connectionLost();
        this.scheduledExecutorService.shutdownNow();

        if (this.started && this.redisDriver.isConnected()) {
            try {
                this.redisDriver.getCommands().del("%slease:%s".formatted(PREFIX, this.sessionId));
            } catch (final RuntimeException | Error exception) {
                this.bindingMap.keySet().forEach(holder -> this.report(holder, exception));
            }
        }
    }

    public void close() {
        this.closed = true;
        this.pendingMap.values().forEach(pending -> pending.getFuture().completeExceptionally(new SynchronizationException("Redis disconnected before cache synchronization completed")));
        this.scheduledExecutorService.shutdownNow();

        if (this.started) {
            this.admissionMap.values().forEach(admission -> {
                admission.getReadyFuture().completeExceptionally(new SynchronizationException("Cache owner has stopped"));
                this.removeRegistration(admission);
            });
            this.redisDriver.getCommands().del("%slease:%s".formatted(PREFIX, this.sessionId));
        }

        this.admissionMap.clear();
        this.bindingMap.clear();
        this.deliveryMap.clear();
        this.repairMap.values().forEach(repair -> {
            if (repair.getScheduledFuture() != null) {
                repair.getScheduledFuture().cancel(false);
            }
        });
        this.repairMap.clear();
        this.executorService.shutdown();
    }

    private void report(final String holderId, final Throwable exception) {
        final Binding binding = this.bindingMap.get(holderId);
        if (binding != null) {
            DatabaseCallbacks.dispatch(() -> binding.getFailureConsumer().accept(exception), failure -> System.getLogger(RedisEntityCoordinator.class.getName()).log(System.Logger.Level.ERROR, "Entity synchronization reporter failed", failure));
        }
    }

    private String ownerKey(final String namespace, final UUID id) {
        return "%scache:owners:%s".formatted(namespace, id);
    }

    private String generationKey(final String namespace, final UUID id) {
        return "%scache:generation:%s".formatted(namespace, id);
    }

    private String membershipKey(final String session) {
        return "%smembers:%s".formatted(PREFIX, session);
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class Repair {

        private final AtomicInteger attempt = new AtomicInteger();

        private final AtomicLong lastWarningNanos = new AtomicLong(System.nanoTime());

        private final long startedNanos = System.nanoTime();

        private final String namespace;

        private final UUID id;

        private final String holderId;

        private volatile ScheduledFuture<?> scheduledFuture;
    }

    @AllArgsConstructor
    @Getter
    private static final class Binding {

        private final String namespace;
        private final Consumer<UUID> refreshConsumer;
        private final Runnable reconnectRunnable;
        private final Consumer<Throwable> failureConsumer;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    public static final class Admission {

        private final CompletableFuture<Void> readyFuture = new CompletableFuture<>();

        private final long admissionThreadId = Thread.currentThread().threadId();

        private final String namespace;

        private final UUID id;

        private final String holderId, registration;

        private long generation;
    }

    @Getter
    private static final class PendingAcknowledgements {

        private final CompletableFuture<Void> future = new CompletableFuture<>();

        private final Set<String> acknowledgedSet = new HashSet<>();
        private final Set<String> failedSet = new HashSet<>();

        private Set<String> expectedSet;

        private synchronized void expect(final List<String> ownerList) {
            this.expectedSet = Set.copyOf(ownerList);
            this.complete();
        }

        private synchronized void acknowledge(final String holderId, final boolean successful) {
            (successful ? this.acknowledgedSet : this.failedSet).add(holderId);
            this.complete();
        }

        private void complete() {
            if (this.expectedSet == null) {
                return;
            }

            if (this.failedSet.stream().anyMatch(this.expectedSet::contains)) {
                this.future.completeExceptionally(new SynchronizationException("Cache refresh failed for owners %s".formatted(this.failedSet)));
            } else if (this.acknowledgedSet.containsAll(this.expectedSet)) {
                this.future.complete(null);
            }
        }

        private synchronized void timeout() {
            if (!this.future.isDone()) {
                final Set<String> missingSet = new HashSet<>(this.expectedSet);
                missingSet.removeAll(this.acknowledgedSet);
                this.future.completeExceptionally(new SynchronizationException("Cache synchronization timed out waiting for owners %s".formatted(missingSet)));
            }
        }
    }
}