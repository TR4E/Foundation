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
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
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

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

@AllArgsConstructor
@Getter
public final class ContextFactory implements HolderContextFactory {

    private final DatabaseSettings settings;
    private final TenantScope tenantScope;
    private final NamespaceFactory namespaceFactory;
    private final List<StorageProvider> providers;
    private final SharedMarker marker;
    private final BatchQueue batchQueue;
    private final RedisDriver redisDriver;
    private final EntityRegistry registry;

    @Override
    public <EntityType extends Entity> HolderContext<EntityType> create(final Repository<EntityType> repository, final Set<Tier> tiers) {
        final Set<Tier> resolved = EnumSet.copyOf(tiers);

        resolved.retainAll(this.settings.getTiers());

        final List<Property<?, ?>> properties = PropertyRegistry.getProperties(repository.getEntityType());

        final EntityCodec<EntityType> codec = new EntityCodec<>(new EntityInstantiator<>(repository.getEntityType()), properties);

        final EntityPayloadConverter<EntityType> converter = new EntityPayloadConverter<>(codec);

        final Namespace<UUID, EntityType> namespace = this.namespaceFactory.entity(repository.getEntityType(), repository.getTable(), converter);

        final RedisEntityStorage<EntityType> redisStorage = this.redis(namespace, codec, resolved);

        final LinkedHashMap<Tier, Storage<UUID, EntityType>> storageMap = this.storages(namespace, redisStorage, resolved);

        final IndexStores indexStores = new IndexStores(repository.getEntityType(), resolved, this.providers, this.namespaceFactory::index);

        return new HolderContext<>(resolved, repository.getTable(), this.tenant(), repository, storageMap, this.lookup(repository, storageMap, converter, resolved), indexStores, this.authority(repository, redisStorage, properties), this.persistence(repository, redisStorage != null, resolved), this.subscriber(repository.getTable(), redisStorage), this.unsubscriber(redisStorage), ValueCodec::decode, this.version(redisStorage), this.refresher(storageMap, indexStores), this.settings.getRefreshInterval());
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
            storageMap.put(Tier.LOCAL, new LocalStorage<>(namespace));
        }

        if (redisStorage != null) {
            storageMap.put(Tier.REDIS, redisStorage);
        }

        return storageMap;
    }

    private <EntityType extends Entity> EntityLookup<EntityType> lookup(final Repository<EntityType> repository, final LinkedHashMap<Tier, Storage<UUID, EntityType>> storageMap, final ValueConverter<EntityType, String> converter, final Set<Tier> tiers) {
        final Stampede stampede = new Stampede(this.marker, this.settings.getMarkerLease(), this.settings.getMarkerTimeout());

        final LoadGuard<EntityType> guard = new LoadGuard<>(stampede, converter, id -> "load:" + repository.getTable() + ":" + id);

        final Backfill<EntityType> backfill = new Backfill<>(storageMap, id -> storageMap.containsKey(Tier.LOCAL) && storageMap.get(Tier.LOCAL).contains(id));

        return new EntityLookup<>(tiers, storageMap, repository, new InFlight<>(), guard, backfill);
    }

    private <EntityType extends Entity> ToLongFunction<Write> authority(final Repository<EntityType> repository, final RedisEntityStorage<EntityType> storage, final List<Property<?, ?>> properties) {
        if (storage == null) {
            return repository::apply;
        }

        final RedisEntityWriter writer = new RedisEntityWriter(this.redisDriver, properties);

        return write -> this.write(writer, storage, write);
    }

    private <EntityType extends Entity> long write(final RedisEntityWriter writer, final RedisEntityStorage<EntityType> storage, final Write write) {
        final UUID id = write.getKey().getId();

        if (write.getKind() == WriteKind.DELETE) {
            writer.delete(storage.path(id), storage.channel(id), write.getExpectedVersion());
            return 0L;
        }

        return writer.apply(storage.path(id), storage.channel(id), write, this.settings.getEntityTtl());
    }

    private Consumer<Write> persistence(final Repository<?> repository, final boolean redis, final Set<Tier> tiers) {
        if (!redis || !tiers.contains(Tier.POSTGRES)) {
            return _ -> {};
        }

        if (this.batchQueue == null) {
            return write -> repository.apply(unversioned(write));
        }

        return write -> this.batchQueue.add(unversioned(write));
    }

    private <EntityType extends Entity> BiConsumer<UUID, Consumer<ChangeEvent>> subscriber(final String table, final RedisEntityStorage<EntityType> storage) {
        if (storage == null) {
            return (_, _) -> {};
        }

        return (id, listener) -> this.redisDriver.subscribe(storage.channel(id), message -> this.accept(listener, table, id, message));
    }

    private <EntityType extends Entity> Consumer<UUID> unsubscriber(final RedisEntityStorage<EntityType> storage) {
        if (storage == null) {
            return _ -> {};
        }

        return id -> this.redisDriver.unsubscribe(storage.channel(id));
    }

    private <EntityType extends Entity> ToLongFunction<UUID> version(final RedisEntityStorage<EntityType> storage) {
        if (storage == null) {
            return _ -> 0L;
        }

        return storage::version;
    }

    private <EntityType extends Entity> Consumer<EntityType> refresher(final LinkedHashMap<Tier, Storage<UUID, EntityType>> storageMap, final IndexStores indexStores) {
        final Storage<UUID, EntityType> storage = storageMap.get(Tier.REDIS);

        if (storage == null) {
            return _ -> {};
        }

        final IndexUpdater updater = new IndexUpdater(indexStores);

        return entity -> {
            storage.refresh(entity.getId());
            updater.refresh(entity);
        };
    }

    private void accept(final Consumer<ChangeEvent> listener, final String table, final UUID id, final String message) {
        if (RedisEvents.origin(message).equals(this.redisDriver.getInstanceId())) {
            return;
        }

        listener.accept(RedisEvents.decode(new EntityKey(table, this.tenant(), id), message));
    }

    private static Write unversioned(final Write write) {
        return new Write(write.getKey(), write.getKind(), write.getChanges(), 0L);
    }

    private String tenant() {
        return this.tenantScope.getTenant().map(Tenant::getName).orElse(null);
    }
}