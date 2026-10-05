package me.trae.foundation.injector.core.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public final class ApplicationRegistry {

    private final LinkedHashMap<Class<?>, ApplicationContext> contextMap = new LinkedHashMap<>();
    private final LinkedHashMap<Class<?>, PendingApplication> pendingMap = new LinkedHashMap<>();

    public void register(final ApplicationContext applicationContext) {
        this.contextMap.put(applicationContext.getApplicationClass(), applicationContext);
    }

    public void unregister(final Class<?> applicationClass) {
        this.contextMap.remove(applicationClass);
    }

    public void addPending(final PendingApplication pendingApplication) {
        this.pendingMap.put(pendingApplication.getApplication().getClass(), pendingApplication);
    }

    public void removePending(final Class<?> applicationClass) {
        this.pendingMap.remove(applicationClass);
    }

    public boolean isKnown(final Class<?> applicationClass) {
        return this.contextMap.containsKey(applicationClass) || this.pendingMap.containsKey(applicationClass);
    }

    public boolean isReady(final List<Class<?>> dependencies) {
        return dependencies.stream().allMatch(this.contextMap::containsKey);
    }

    public Optional<ApplicationContext> getContext(final Class<?> applicationClass) {
        return Optional.ofNullable(this.contextMap.get(applicationClass));
    }

    public Optional<PendingApplication> pollReady() {
        final Optional<PendingApplication> pendingApplication = this.pendingMap.values().stream()
                .filter(pending -> this.isReady(pending.getDependencies()))
                .findFirst();

        pendingApplication.ifPresent(pending -> this.pendingMap.remove(pending.getApplication().getClass()));

        return pendingApplication;
    }

    public List<ApplicationContext> getDependents(final Class<?> applicationClass) {
        return this.contextMap.values().stream()
                .filter(applicationContext -> applicationContext.getDependencies().contains(applicationClass))
                .toList();
    }
}