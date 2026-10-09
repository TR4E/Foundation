package me.trae.foundation.database.lookup;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.api.holder.LookupTier;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.query.EntityPage;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.repository.EntityRepository;
import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.lookup.inflight.InFlightLookup;
import me.trae.foundation.database.lookup.step.LookupStep;
import me.trae.foundation.database.storage.local.LocalStorage;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LookupTest {

    private static final EntityProperty<Member, String> NAME = EntityProperty.register(Member.class, "name", Member::getName, Member::setName, String.class, true);

    private final Map<String, String> localMap = new HashMap<>();
    private final Map<String, String> redisMap = new HashMap<>();
    private final Map<String, String> databaseMap = new HashMap<>();

    private final TieredLookup<String, String> tieredLookup = new TieredLookup<>(List.of(
            LookupStep.of(LookupTier.LOCAL, key -> Optional.ofNullable(this.localMap.get(key)), this.localMap::put),
            LookupStep.of(LookupTier.REDIS, key -> Optional.ofNullable(this.redisMap.get(key)), this.redisMap::put),
            LookupStep.of(LookupTier.DATABASE, key -> Optional.ofNullable(this.databaseMap.get(key)))
    ));

    private final MemoryRepository repository = new MemoryRepository();
    private final LocalStorage<UUID, Member> localStorage = new LocalStorage<>();

    @Test
    void walksTiersAndWritesBack() {
        this.databaseMap.put("a", "database");

        assertEquals(Optional.of("database"), this.tieredLookup.lookup("a"));
        assertEquals("database", this.localMap.get("a"));
        assertEquals("database", this.redisMap.get("a"));
    }

    @Test
    void skipsTiersNotAskedFor() {
        this.localMap.put("a", "local");
        this.databaseMap.put("a", "database");

        assertEquals(Optional.of("database"), this.tieredLookup.lookup("a", EnumSet.of(LookupTier.DATABASE)));
        assertTrue(this.tieredLookup.lookup("missing").isEmpty());
    }

    @Test
    void lookupAllMixesTiersInRequestedOrder() {
        this.localMap.put("a", "1");
        this.redisMap.put("b", "2");
        this.databaseMap.put("c", "3");

        assertEquals(List.of("c", "a", "b"), List.copyOf(this.tieredLookup.lookupAll(List.of("c", "a", "x", "b")).keySet()));
        assertEquals("3", this.localMap.get("c"));
    }

    @Test
    void inFlightLookupCoalescesConcurrentCallers() throws Exception {
        final InFlightLookup<String> inFlightLookup = new InFlightLookup<>();
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        try (final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<Optional<String>> first = executorService.submit(() -> inFlightLookup.compute("key", () -> {
                calls.incrementAndGet();
                started.countDown();
                awaitQuietly(release);
                return Optional.of("value");
            }));

            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));

                final Future<Optional<String>> second = executorService.submit(() -> inFlightLookup.compute("key", () -> {
                    calls.incrementAndGet();
                    return Optional.of("other");
                }));

                Thread.sleep(100);
                release.countDown();

                assertEquals(Optional.of("value"), first.get(5, TimeUnit.SECONDS));
                assertEquals(Optional.of("value"), second.get(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }

        assertEquals(1, calls.get());
        assertEquals(0, inFlightLookup.getInFlightCount());
    }

    @Test
    void singletonCachesDatabaseHitsLocally() {
        final Member member = this.repository.add("trae");
        final TieredLookup<UUID, Member> idLookup = EntityLookups.byId(InstanceMode.SINGLETON, this.localStorage, null, this.repository);

        assertSame(member, idLookup.lookup(member.getId()).orElseThrow());
        assertSame(member, idLookup.lookup(member.getId()).orElseThrow());
        assertEquals(1, this.repository.readCount.get());
    }

    @Test
    void multiInstanceAlwaysReadsTheDatabase() {
        final Member member = this.repository.add("trae");
        final TieredLookup<UUID, Member> idLookup = EntityLookups.byId(InstanceMode.MULTI_INSTANCE, this.localStorage, null, this.repository);

        idLookup.lookup(member.getId());
        idLookup.lookup(member.getId());

        assertEquals(2, this.repository.readCount.get());
        assertTrue(this.localStorage.get(member.getId()).isEmpty());
    }

    @Test
    void findsByPropertyLocallyThenInTheDatabase() {
        final Member cached = new Member(UUID.randomUUID());
        cached.setName("cached");
        this.localStorage.put(cached.getId(), cached);
        final Member stored = this.repository.add("stored");

        final TieredLookup<String, Member> nameLookup = EntityLookups.byProperty(InstanceMode.SINGLETON, NAME, this.localStorage, null, null, this.repository);

        assertSame(cached, nameLookup.lookup("cached").orElseThrow());
        assertEquals(0, this.repository.readCount.get());
        assertSame(stored, nameLookup.lookup("stored").orElseThrow());
        assertTrue(this.localStorage.get(stored.getId()).isPresent());
    }

    @Test
    void findPageClampsPastTheEnd() {
        this.repository.add("alice");

        final EntityPage<Member> entityPage = this.repository.findPage(Query.of(Member.class), 999, 10);

        assertEquals(1, entityPage.getPage());
        assertEquals(1L, entityPage.getTotalCount());
        assertEquals(1, entityPage.getEntities().size());
    }

    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class Member implements Entity {

        private final UUID id;
        private String name;
    }

    private static final class MemoryRepository implements EntityRepository<Member> {

        private final Map<UUID, Member> memberMap = new LinkedHashMap<>();
        private final AtomicInteger readCount = new AtomicInteger();

        private Member add(final String name) {
            final Member member = new Member(UUID.randomUUID());
            member.setName(name);
            this.memberMap.put(member.getId(), member);
            return member;
        }

        @Override
        public Class<Member> getEntityType() {
            return Member.class;
        }

        @Override
        public String getTable() {
            return "members";
        }

        @Override
        public TenantScope getTenantScope() {
            return TenantScope.NONE;
        }

        @Override
        public List<EntityProperty<?, ?>> getProperties() {
            return List.of(NAME);
        }

        @Override
        public Optional<Member> findById(final UUID id) {
            this.readCount.incrementAndGet();
            return Optional.ofNullable(this.memberMap.get(id));
        }

        @Override
        public List<Member> findManyById(final Collection<UUID> ids) {
            this.readCount.incrementAndGet();
            return ids.stream().map(this.memberMap::get).filter(Objects::nonNull).toList();
        }

        @Override
        public Optional<Member> findOne(final Query<Member> query) {
            this.readCount.incrementAndGet();
            return this.memberMap.values().stream().filter(member -> query.getConditionList().stream().allMatch(condition -> Objects.equals(condition.getEntityProperty().getValue(member), condition.getValue()))).findFirst();
        }

        @Override
        public List<Member> findMany(final Query<Member> query) {
            return this.findOne(query).stream().toList();
        }

        @Override
        public long count(final Query<Member> query) {
            return this.findMany(query).size();
        }

        @Override
        public boolean exists(final Query<Member> query) {
            return this.findOne(query).isPresent();
        }

        @Override
        public EntityPage<Member> findPage(final Query<Member> query, final int page, final int size) {
            final List<Member> entityList = this.findMany(query);

            final long totalCount = entityList.size();
            final int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / (double) size));
            final int current = Math.clamp(page, 1, totalPages);

            return new EntityPage<>(entityList.stream().skip((long) (current - 1) * size).limit(size).toList(), totalCount, current, size);
        }

        @Override
        public void save(final Member member) {
            this.memberMap.put(member.getId(), member);
        }

        @Override
        public void delete(final Member member) {
            this.memberMap.remove(member.getId());
        }
    }
}