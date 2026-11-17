package me.trae.foundation.database.verification;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.converter.ValueConverter;
import me.trae.foundation.database.api.declaration.DeclarationLoader;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.event.ChangeEvent;
import me.trae.foundation.database.api.index.GroupedIndex;
import me.trae.foundation.database.api.index.UniqueIndex;
import me.trae.foundation.database.api.marker.SharedMarker;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.storage.StorageProvider;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;
import me.trae.foundation.database.concurrency.InFlight;
import me.trae.foundation.database.concurrency.Stampede;
import me.trae.foundation.database.holder.context.HolderContext;
import me.trae.foundation.database.holder.context.HolderContextFactory;
import me.trae.foundation.database.holder.entity.EntityHolder;
import me.trae.foundation.database.index.IndexStores;
import me.trae.foundation.database.local.LocalMarker;
import me.trae.foundation.database.local.LocalStorage;
import me.trae.foundation.database.local.LocalStorageProvider;
import me.trae.foundation.database.lookup.Backfill;
import me.trae.foundation.database.lookup.EntityLookup;
import me.trae.foundation.database.lookup.LoadGuard;
import me.trae.foundation.database.postgres.driver.PostgresDriver;
import me.trae.foundation.database.postgres.repository.PostgresRepository;
import me.trae.foundation.database.redis.codec.ValueCodec;
import me.trae.foundation.database.tenant.TenantScope;
import me.trae.foundation.injector.api.annotation.Application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@UtilityClass
public final class DatabaseFixture {

    public static Account account() {
        final Account account = new Account(UUID.randomUUID());

        account.setName("name-%s".formatted(account.getId()));
        account.setGroup("group");

        return account;
    }

    public static AccountRepository repository() {
        final AccountRepository accountRepository = mock(AccountRepository.class);

        when(accountRepository.getEntityType()).thenReturn(Account.class);
        when(accountRepository.getTable()).thenReturn("accounts");
        when(accountRepository.apply(any())).thenReturn(1L);

        return accountRepository;
    }

    public static Namespace<UUID, Account> namespace(final String name) {
        return new Namespace<>(name, Account.class, UUID::toString, new AccountText(), Duration.ofSeconds(10));
    }

    public static Namespace<Object, UUID> indexNamespace(final String name) {
        return new Namespace<>(name, UUID.class, String::valueOf, new UuidText(), Duration.ofSeconds(10));
    }

    public static Write write(final UUID id, final WriteKind writeKind, final long version, final Change... changes) {
        return new Write(new me.trae.foundation.database.api.key.EntityKey("accounts", null, id), writeKind, List.of(changes), version);
    }

    public static Change set(final String column, final Object value) {
        return new Change(column, ChangeType.SET, value);
    }

    public static Change increment(final long value) {
        return new Change("counter", ChangeType.INCREMENT, value);
    }

    public static LinkedHashMap<Tier, Storage<UUID, Account>> storages(final Set<Tier> tiers) {
        final LinkedHashMap<Tier, Storage<UUID, Account>> storageMap = new LinkedHashMap<>();

        if (tiers.contains(Tier.LOCAL)) {
            storageMap.put(Tier.LOCAL, new LocalStorage<>(namespace("local")));
        }

        if (tiers.contains(Tier.REDIS)) {
            storageMap.put(Tier.REDIS, new LocalStorage<>(namespace("fake-redis")));
        }

        return storageMap;
    }

    public static EntityLookup<Account> lookup(
            final AccountRepository accountRepository,
            final Set<Tier> tiers,
            final LinkedHashMap<Tier, Storage<UUID, Account>> storageMap,
            final SharedMarker sharedMarker
    ) {
        return new EntityLookup<>(
                tiers,
                storageMap,
                accountRepository,
                new InFlight<>(),
                new LoadGuard<>(new Stampede(sharedMarker, Duration.ofSeconds(5), Duration.ofSeconds(5)), new AccountText(), id -> "load:" + id),
                new Backfill<>(storageMap, id -> storageMap.containsKey(Tier.LOCAL) && storageMap.get(Tier.LOCAL).contains(id))
        );
    }

    public static HolderContext<Account> context(
            final AccountRepository accountRepository,
            final Set<Tier> tiers,
            final LinkedHashMap<Tier, Storage<UUID, Account>> storageMap,
            final SharedMarker sharedMarker,
            final ToLongFunction<Write> authority,
            final Consumer<Write> persistence,
            final BiConsumer<UUID, Consumer<ChangeEvent>> subscriber,
            final Consumer<UUID> unsubscriber,
            final List<StorageProvider> providerList
    ) {
        DeclarationLoader.load(AccountDeclaration.class);

        return new HolderContext<>(
                tiers,
                "accounts",
                null,
                accountRepository,
                storageMap,
                lookup(accountRepository, tiers, storageMap, sharedMarker),
                new IndexStores(Account.class, tiers, providerList, index -> indexNamespace(index.getName())),
                authority,
                persistence,
                subscriber,
                unsubscriber,
                ValueCodec::decode,
                _ -> 0L,
                _ -> {},
                Duration.ofSeconds(10)
        );
    }

    public static AccountHolder holder(final AccountRepository accountRepository, final Set<Tier> tiers) {
        final ContextFactory contextFactory = new ContextFactory(resolved -> context(
                accountRepository,
                resolved,
                storages(resolved),
                new LocalMarker(),
                accountRepository::apply,
                _ -> {},
                (_, _) -> {},
                _ -> {},
                List.of(new LocalStorageProvider())
        ));

        return new AccountHolder(accountRepository, contextFactory, tiers);
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    public static final class Account implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private Long counter = 0L;

        private String name, group;
    }

    @UtilityClass
    public static final class AccountDeclaration {

        public static final Property<Account, String> NAME = Property.persistent(Account.class, "name", String.class, Account::getName, Account::setName);
        public static final Property<Account, String> GROUP = Property.persistent(
                Account.class,
                "account_group",
                String.class,
                Account::getGroup,
                Account::setGroup
        );

        public static final Property<Account, Long> COUNTER = Property.persistent(
                Account.class,
                "counter",
                Long.class,
                Account::getCounter,
                Account::setCounter
        );

        public static final UniqueIndex<Account, String> NAME_INDEX = UniqueIndex.of("account_name", NAME);

        public static final GroupedIndex<Account, String> GROUP_INDEX = GroupedIndex.of("account_group", GROUP, Set.of(Tier.LOCAL));
    }

    public static class AccountRepository extends PostgresRepository<Account> {

        public AccountRepository(final PostgresDriver postgresDriver, final TenantScope tenantScope) {
            super(Account.class, "accounts", postgresDriver, tenantScope, AccountDeclaration.class);
        }
    }

    @AllArgsConstructor
    @Getter
    public static final class ContextFactory implements HolderContextFactory {

        private final List<EntityHolder<?, ?>> holderList = new ArrayList<>();

        private final Function<Set<Tier>, HolderContext<Account>> contextFunction;

        @Override
        @SuppressWarnings("unchecked")
        public <EntityType extends Entity> HolderContext<EntityType> create(final Repository<EntityType> repository, final Set<Tier> tiers) {
            return (HolderContext<EntityType>) this.contextFunction.apply(tiers);
        }

        @Override
        public void register(final EntityHolder<?, ?> entityHolder) {
            this.holderList.add(entityHolder);
        }
    }

    public static class AccountHolder extends EntityHolder<Account, AccountRepository> {

        public AccountHolder(final AccountRepository accountRepository, final HolderContextFactory holderContextFactory, final Set<Tier> tiers) {
            super(Account.class, accountRepository, holderContextFactory, tiers, AccountDeclaration.class);
        }

        @Override
        public boolean decrementIfAtLeast(final Account account, final Property<Account, Long> property, final long delta, final long floor) {
            throw new UnsupportedOperationException("Not implemented by production EntityHolder");
        }
    }

    public static final class LocalBean extends AccountHolder {

        public LocalBean(final AccountRepository accountRepository, final HolderContextFactory holderContextFactory) {
            super(accountRepository, holderContextFactory, Set.of(Tier.LOCAL, Tier.POSTGRES));
        }
    }

    public static final class RedisBean extends AccountHolder {

        public RedisBean(final AccountRepository accountRepository, final HolderContextFactory holderContextFactory) {
            super(accountRepository, holderContextFactory, Set.of(Tier.REDIS, Tier.POSTGRES));
        }
    }

    public static final class AllBean extends AccountHolder {

        public AllBean(final AccountRepository accountRepository, final HolderContextFactory holderContextFactory) {
            super(accountRepository, holderContextFactory, Tier.ALL);
        }
    }

    @Application
    public static final class TestApplication {

    }

    public static final class AccountText implements ValueConverter<Account, String> {

        @Override
        public Class<String> getStoredType() {
            return String.class;
        }

        @Override
        public String serialize(final Account account) {
            return "%s|%s|%s|%s".formatted(account.getId(), account.getName(), account.getGroup(), account.getCounter());
        }

        @Override
        public Account deserialize(final String value) {
            final String[] partArray = value.split("\\|");
            final Account account = new Account(UUID.fromString(partArray[0]));

            account.setName(partArray[1]);
            account.setGroup(partArray[2]);
            account.setCounter(Long.parseLong(partArray[3]));

            return account;
        }
    }

    public static final class UuidText implements ValueConverter<UUID, String> {

        @Override
        public Class<String> getStoredType() {
            return String.class;
        }

        @Override
        public String serialize(final UUID value) {
            return value.toString();
        }

        @Override
        public UUID deserialize(final String value) {
            return UUID.fromString(value);
        }
    }

    public static final class MemoryMarker implements SharedMarker {

        private final ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<String>> resultMap = new ConcurrentHashMap<>();

        @Override
        public boolean claim(final String key, final Duration lease) {
            return this.resultMap.putIfAbsent(key, new java.util.concurrent.CompletableFuture<>()) == null;
        }

        @Override
        public Optional<String> await(final String key, final Duration timeout) {
            try {
                return Optional.of(this.resultMap.get(key).get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
            } catch (final Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        public void release(final String key, final String payload) {
            this.resultMap.get(key).complete(payload);
        }

        @Override
        public void release(final String key) {
            this.resultMap.remove(key);
        }
    }
}