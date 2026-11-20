package me.trae.foundation.database.core.context;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.converter.ValueConverter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.EntityInstantiator;
import me.trae.foundation.database.api.event.ChangeEvent;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.marker.SharedMarker;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.storage.StorageProvider;
import me.trae.foundation.database.api.storage.VersionedEntity;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteGate;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.WriteResult;
import me.trae.foundation.database.batch.BatchQueue;
import me.trae.foundation.database.concurrency.InFlight;
import me.trae.foundation.database.concurrency.Stampede;
import me.trae.foundation.database.core.converter.EntityPayloadConverter;
import me.trae.foundation.database.core.namespace.NamespaceFactory;
import me.trae.foundation.database.core.registry.EntityRegistry;
import me.trae.foundation.database.core.settings.DatabaseSettings;
import me.trae.foundation.database.holder.context.HolderContext;
import me.trae.foundation.database.holder.context.HolderContextFactory;
import me.trae.foundation.database.holder.entity.EntityHolder;
import me.trae.foundation.database.index.IndexStores;
import me.trae.foundation.database.index.IndexUpdater;
import me.trae.foundation.database.local.LocalStorage;
import me.trae.foundation.database.lookup.Backfill;
import me.trae.foundation.database.lookup.EntityLookup;
import me.trae.foundation.database.lookup.LoadGuard;
import me.trae.foundation.database.redis.codec.EntityCodec;
import me.trae.foundation.database.redis.codec.ValueCodec;
import me.trae.foundation.database.redis.driver.RedisDriver;
import me.trae.foundation.database.redis.event.RedisEvents;
import me.trae.foundation.database.redis.storage.RedisEntityStorage;
import me.trae.foundation.database.redis.write.RedisEntityWriter;
import me.trae.foundation.database.tenant.Tenant;
import me.trae.foundation.database.tenant.TenantScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public final class ContextFactory implements HolderContextFactory {

    private final DatabaseSettings settings;
    private final TenantScope tenantScope;
    private final NamespaceFactory namespaceFactory;
    private final List<StorageProvider> providers;
    private final SharedMarker marker;
    private final BatchQueue batchQueue;
    private final WriteGate gate;
    private final RedisDriver redisDriver;
    private final EntityRegistry registry;

    @Override
    public <EntityType extends Entity> HolderContext<EntityType> create(final Repository<EntityType> repository, final Set<Tier> tiers) {
        final Set<Tier> resolved = EnumSet.noneOf(Tier.class);

        resolved.addAll(tiers);
        resolved.retainAll(this.settings.getTiers());

        final List<Property<?, ?>> properties = PropertyRegistry.getProperties(repository.getEntityType());

        final EntityCodec<EntityType> codec = new EntityCodec<>(new EntityInstantiator<>(repository.getEntityType()), properties);

        final EntityPayloadConverter<EntityType> converter = new EntityPayloadConverter<>(codec);

        final Namespace<UUID, EntityType> namespace = this.namespaceFactory.entity(repository.getEntityType(), repository.getTable(), converter);

        final RedisEntityStorage<EntityType> redisStorage = this.redis(namespace, codec, resolved);

        final LinkedHashMap<Tier, Storage<UUID, EntityType>> storageMap = this.storages(namespace, redisStorage, resolved);

        final IndexStores indexStores = new IndexStores(repository.getEntityType(), resolved, this.providers, this.namespaceFactory::index);

        return new HolderContext<>(resolved, repository.getTable(), this.tenant(), repository, storageMap, this.lookup(repository, storageMap, converter, resolved), indexStores, this.authority(repository, redisStorage, properties, resolved), this.persistence(repository, redisStorage != null, resolved), this.gate, this.subscriber(repository.getTable(), redisStorage), this.unsubscriber(redisStorage), ValueCodec::decode, this.version(redisStorage), this.versions(redisStorage), this.snapshot(redisStorage), this.snapshots(redisStorage), this.refresher(storageMap, indexStores), this.settings.getRefreshInterval());
    }

    @Override
    public void register(final EntityHolder<?, ?> holder) {
        this.registry.register(holder.getRepository(), holder);
    }

    private <EntityType extends Entity> RedisEntityStorage<EntityType> redis(final Namespace<UUID, EntityType> namespace, final EntityCodec<EntityType> codec, final Set<Tier> tiers) {
        if (this.redisDriver == null || !tiers.contains(Tier.REDIS)) {
            return null;
        }

        return new RedisEntityStorage<>(namespace, this.redisDriver, codec);
    }

    private <EntityType extends Entity> LinkedHashMap<Tier, Storage<UUID, EntityType>> storages(final Namespace<UUID, EntityType> namespace, final RedisEntityStorage<EntityType> redisStorage, final Set<Tier> tiers) {
        final LinkedHashMap<Tier, Storage<UUID, EntityType>> storageMap = new LinkedHashMap<>();

        if (tiers.contains(Tier.LOCAL)) {
            storageMap.put(Tier.LOCAL, new LocalStorage<>(namespace, this.settings.getLocalLimit(), this.settings.getLocalTtl()));
        }

        if (redisStorage != null) {
            storageMap.put(Tier.REDIS, redisStorage);
        }

        return storageMap;
    }

    private <EntityType extends Entity> EntityLookup<EntityType> lookup(final Repository<EntityType> repository, final LinkedHashMap<Tier, Storage<UUID, EntityType>> storageMap, final ValueConverter<EntityType, String> converter, final Set<Tier> tiers) {
        final LoadGuard<EntityType> guard = new LoadGuard<>(this.stampede(tiers), converter, id -> "load:" + this.tenant() + ":" + repository.getTable() + ":" + id);

        final Backfill<EntityType> backfill = new Backfill<>(storageMap, id -> storageMap.containsKey(Tier.LOCAL) && storageMap.get(Tier.LOCAL).contains(id));

        return new EntityLookup<>(tiers, storageMap, repository, new InFlight<>(), guard, backfill);
    }

    private Stampede stampede(final Set<Tier> tiers) {
        if (this.marker == null || !tiers.contains(Tier.REDIS)) {
            return null;
        }

        return new Stampede(this.marker, this.settings.getMarkerLease(), this.settings.getMarkerTimeout());
    }

    private <EntityType extends Entity> Function<Write, WriteResult> authority(final Repository<EntityType> repository, final RedisEntityStorage<EntityType> storage, final List<Property<?, ?>> properties, final Set<Tier> tiers) {
        if (storage != null) {
            final RedisEntityWriter writer = new RedisEntityWriter(this.redisDriver, properties);

            return write -> this.write(writer, storage, write);
        }

        if (!tiers.contains(Tier.POSTGRES)) {
            return _ -> new WriteResult(0L, null);
        }

        return repository::apply;
    }

    private <EntityType extends Entity> WriteResult write(final RedisEntityWriter writer, final RedisEntityStorage<EntityType> storage, final Write write) {
        final String path = storage.path(write.getKey().getId());

        final String channel = RedisEntityStorage.channel(path);

        if (write.getKind() == WriteKind.DELETE) {
            writer.delete(path, channel, write.getExpectedVersion());

            return new WriteResult(0L, null);
        }

        return writer.apply(path, channel, write, this.settings.getEntityTtl());
    }

    private Consumer<Write> persistence(final Repository<?> repository, final boolean redis, final Set<Tier> tiers) {
        if (!redis || !tiers.contains(Tier.POSTGRES)) {
            return _ -> {};
        }

        if (this.batchQueue == null) {
            return repository::apply;
        }

        return this.batchQueue::add;
    }

    private <EntityType extends Entity> BiConsumer<Collection<UUID>, Consumer<ChangeEvent>> subscriber(final String table, final RedisEntityStorage<EntityType> storage) {
        if (storage == null) {
            return (_, _) -> {};
        }

        return (ids, listener) -> this.redisDriver.subscribe(this.channels(storage, ids, table, listener));
    }

    private <EntityType extends Entity> Consumer<UUID> unsubscriber(final RedisEntityStorage<EntityType> storage) {
        if (storage == null) {
            return _ -> {};
        }

        return id -> this.redisDriver.unsubscribe(storage.channel(id));
    }

    private <EntityType extends Entity> LinkedHashMap<String, Consumer<String>> channels(final RedisEntityStorage<EntityType> storage, final Collection<UUID> ids, final String table, final Consumer<ChangeEvent> listener) {
        final LinkedHashMap<String, Consumer<String>> listenerMap = new LinkedHashMap<>();

        ids.forEach(id -> listenerMap.put(storage.channel(id), message -> this.accept(listener, table, id, message)));

        return listenerMap;
    }

    private <EntityType extends Entity> Function<UUID, Optional<VersionedEntity<EntityType>>> snapshot(final RedisEntityStorage<EntityType> storage) {
        if (storage == null) {
            return _ -> Optional.empty();
        }

        return storage::snapshot;
    }

    private <EntityType extends Entity> Function<Collection<UUID>, Map<UUID, VersionedEntity<EntityType>>> snapshots(final RedisEntityStorage<EntityType> storage) {
        if (storage == null) {
            return _ -> Collections.emptyMap();
        }

        return storage::snapshots;
    }

    private <EntityType extends Entity> ToLongFunction<UUID> version(final RedisEntityStorage<EntityType> storage) {
        if (storage == null) {
            return _ -> 0L;
        }

        return storage::version;
    }

    private <EntityType extends Entity> Function<Collection<UUID>, Map<UUID, Long>> versions(final RedisEntityStorage<EntityType> storage) {
        if (storage == null) {
            return _ -> Collections.emptyMap();
        }

        return storage::versions;
    }

    private <EntityType extends Entity> Consumer<List<EntityType>> refresher(final LinkedHashMap<Tier, Storage<UUID, EntityType>> storageMap, final IndexStores indexStores) {
        final Storage<UUID, EntityType> storage = storageMap.get(Tier.REDIS);

        if (storage == null) {
            return _ -> {};
        }

        final IndexUpdater updater = new IndexUpdater(indexStores);

        return entities -> {
            storage.refreshAll(entities.stream().map(Entity::getId).collect(Collectors.toCollection(ArrayList::new)));
            updater.refresh(entities);
        };
    }

    private void accept(final Consumer<ChangeEvent> listener, final String table, final UUID id, final String message) {
        if (RedisEvents.origin(message).equals(this.redisDriver.getInstanceId())) {
            return;
        }

        listener.accept(RedisEvents.decode(new EntityKey(table, this.tenant(), id), message));
    }

    private String tenant() {
        return this.tenantScope.getTenant().map(Tenant::getName).orElse(null);
    }
}