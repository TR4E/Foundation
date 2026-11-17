package me.trae.foundation.database.verification;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.converter.ValueConverter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.event.ChangeEvent;
import me.trae.foundation.database.api.index.GroupedIndex;
import me.trae.foundation.database.api.index.UniqueIndex;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.marker.SharedMarker;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;
import me.trae.foundation.database.batch.BatchQueue;
import me.trae.foundation.database.concurrency.InFlight;
import me.trae.foundation.database.concurrency.Stampede;
import me.trae.foundation.database.index.GroupedIndexStore;
import me.trae.foundation.database.index.UniqueIndexStore;
import me.trae.foundation.database.local.LocalMarker;
import me.trae.foundation.database.local.LocalStorage;
import me.trae.foundation.database.lookup.Backfill;
import me.trae.foundation.database.lookup.EntityLookup;
import me.trae.foundation.database.lookup.LoadGuard;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class IsolatedFixesTest {

    @Test
    @SuppressWarnings("unchecked")
    void localOnlyMissCannotSuppressFullLookup() throws Exception {
        final Repository<Probe> repository = mock(Repository.class);
        final SharedMarker sharedMarker = mock(SharedMarker.class);
        final Probe probe = new Probe(UUID.randomUUID());
        final AtomicInteger claims = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        when(repository.findById(probe.getId())).thenReturn(Optional.of(probe));
        when(sharedMarker.claim(any(), any())).thenAnswer(_ -> {
            if (claims.incrementAndGet() == 1) {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }

            return true;
        });

        final EntityLookup<Probe> entityLookup = this.lookup(repository, sharedMarker);

        try (final var executorService = Executors.newFixedThreadPool(2)) {
            final var localFuture = executorService.submit(() -> entityLookup.find(probe.getId(), Set.of(Tier.LOCAL)));

            assertTrue(entered.await(5, TimeUnit.SECONDS));

            try {
                final var fullFuture = executorService.submit(() -> entityLookup.find(probe.getId(), Set.of(Tier.LOCAL, Tier.POSTGRES)));

                assertTrue(fullFuture.get(3, TimeUnit.SECONDS).isPresent(), "Full lookup must not inherit a LOCAL-only miss");
            } finally {
                release.countDown();
            }

            assertTrue(localFuture.get(5, TimeUnit.SECONDS).isEmpty());
            verify(repository, times(1)).findById(probe.getId());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void identicalFullLookupsStillCallRepositoryOnce() throws Exception {
        final Repository<Probe> repository = mock(Repository.class);
        final Probe probe = new Probe(UUID.randomUUID());
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<Thread> readerThread = new AtomicReference<>();

        when(repository.findById(probe.getId())).thenAnswer(_ -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));

            return Optional.of(probe);
        });

        final EntityLookup<Probe> entityLookup = this.lookup(repository, new LocalMarker());

        try (final var executorService = Executors.newFixedThreadPool(2)) {
            final var firstFuture = executorService.submit(() -> entityLookup.find(probe.getId(), Set.of(Tier.LOCAL, Tier.POSTGRES)));

            assertTrue(entered.await(5, TimeUnit.SECONDS));

            final var secondFuture = executorService.submit(() -> {
                readerThread.set(Thread.currentThread());

                return entityLookup.find(probe.getId(), Set.of(Tier.LOCAL, Tier.POSTGRES));
            });
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

            while ((readerThread.get() == null || readerThread.get().getState() != Thread.State.WAITING) && System.nanoTime() < deadline) {
                Thread.sleep(1L);
            }

            assertNotNull(readerThread.get());
            assertEquals(Thread.State.WAITING, readerThread.get().getState());
            release.countDown();

            assertSame(probe, firstFuture.get(5, TimeUnit.SECONDS).orElseThrow());
            assertSame(probe, secondFuture.get(5, TimeUnit.SECONDS).orElseThrow());
            verify(repository, times(1)).findById(probe.getId());
        } finally {
            release.countDown();
        }
    }

    @Test
    void changeEventCarriesWriteKind() {
        final ChangeEvent changeEvent = new ChangeEvent(
                new EntityKey("probe", null, UUID.randomUUID()),
                4L,
                WriteKind.DELETE,
                List.of()
        );

        assertEquals(WriteKind.DELETE, changeEvent.getKind());
        assertEquals(4L, changeEvent.getVersion());
        assertTrue(changeEvent.getChanges().isEmpty());
    }

    @Test
    void uniqueIndexCanBeRemovedByIdWithoutLocalEntity() {
        final Property<Probe, String> property = Property.persistent(Probe.class, "unique_name", String.class, Probe::getName, Probe::setName);
        final UniqueIndex<Probe, String> uniqueIndex = UniqueIndex.of("isolated_unique", property);
        final UniqueIndexStore uniqueIndexStore = new UniqueIndexStore(uniqueIndex, List.of(new LocalStorage<>(this.namespace())));
        final Probe probe = new Probe(UUID.randomUUID());

        probe.setName("indexed");
        uniqueIndexStore.index(probe);

        assertEquals(probe.getId(), uniqueIndexStore.lookup("indexed").orElseThrow());

        uniqueIndexStore.unindex(probe.getId());

        assertTrue(uniqueIndexStore.lookup("indexed").isEmpty());
        assertTrue(this.map(uniqueIndexStore, "keyMap").isEmpty());
    }

    @Test
    void groupedIndexCanBeRemovedByIdWithoutLocalEntity() {
        final Property<Probe, String> property = Property.persistent(Probe.class, "group_name", String.class, Probe::getName, Probe::setName);
        final GroupedIndexStore groupedIndexStore = new GroupedIndexStore(GroupedIndex.of("isolated_group", property, Set.of(Tier.LOCAL)));
        final Probe probe = new Probe(UUID.randomUUID());

        probe.setName("group");
        groupedIndexStore.index(probe);
        groupedIndexStore.unindex(probe.getId());

        assertTrue(groupedIndexStore.lookup("group").isEmpty());
        assertTrue(this.map(groupedIndexStore, "keyMap").isEmpty());
        assertTrue(this.map(groupedIndexStore, "idMap").isEmpty());
    }

    @Test
    void batchKindMatrixRetainsSaveAndDeleteAsRequired() {
        final WriteKind[][] expectedKindArray = {
                {WriteKind.SAVE, WriteKind.SAVE, WriteKind.DELETE},
                {WriteKind.SAVE, WriteKind.UPDATE, WriteKind.DELETE},
                {WriteKind.SAVE, WriteKind.DELETE, WriteKind.DELETE}
        };

        for (final WriteKind existingKind : WriteKind.values()) {
            for (final WriteKind incomingKind : WriteKind.values()) {
                final BatchQueue batchQueue = new BatchQueue();
                final EntityKey entityKey = new EntityKey("probe", null, UUID.randomUUID());

                batchQueue.add(new Write(entityKey, existingKind, List.of(new Change("counter", ChangeType.SET, 1L)), 0L));
                batchQueue.add(new Write(entityKey, incomingKind, List.of(new Change("counter", ChangeType.SET, 2L)), 0L));

                assertEquals(expectedKindArray[existingKind.ordinal()][incomingKind.ordinal()], batchQueue.drain().getFirst().getKind());
                assertEquals(0, batchQueue.size());
            }
        }
    }

    private EntityLookup<Probe> lookup(final Repository<Probe> repository, final SharedMarker sharedMarker) {
        return new EntityLookup<>(
                Set.of(Tier.LOCAL, Tier.POSTGRES),
                new LinkedHashMap<>(),
                repository,
                new InFlight<>(),
                new LoadGuard<>(
                        new Stampede(sharedMarker, Duration.ofSeconds(5), Duration.ofSeconds(5)),
                        new ProbeText(),
                        UUID::toString
                ),
                new Backfill<>(new LinkedHashMap<>(), _ -> false)
        );
    }

    private Namespace<Object, UUID> namespace() {
        return new Namespace<>("isolated", UUID.class, String::valueOf, new UuidText(), Duration.ZERO);
    }

    private java.util.Map<?, ?> map(final Object object, final String name) {
        try {
            final var field = object.getClass().getDeclaredField(name);

            field.setAccessible(true);

            return (java.util.Map<?, ?>) field.get(object);
        } catch (final ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    public static final class Probe implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private String name;
    }

    private static final class ProbeText implements ValueConverter<Probe, String> {

        @Override
        public Class<String> getStoredType() {
            return String.class;
        }

        @Override
        public String serialize(final Probe probe) {
            return probe.getId().toString();
        }

        @Override
        public Probe deserialize(final String value) {
            return new Probe(UUID.fromString(value));
        }
    }

    private static final class UuidText implements ValueConverter<UUID, String> {

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
}