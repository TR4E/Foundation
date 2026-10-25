package me.trae.foundation.minecraft.paper.addon.npc;

import me.trae.foundation.minecraft.paper.addon.npc.search.NpcSearchEngine;
import me.trae.foundation.minecraft.paper.addon.npc.services.NpcService;
import me.trae.foundation.minecraft.paper.addon.npc.types.RespawnableNpc;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilTask;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

final class NpcServiceTest extends NpcTestSupport {

    @Test
    void getNpcByEntityResolvesThroughTheSpawnedIdentifierStamp() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        final Entity entity = this.entity(Entity.class);
        this.entityQueue.add(entity);
        npc.spawn(this.location(0.0D, 0.0D));
        final NpcService npcService = new NpcService(List.of(npc));
        npcService.onLiveDependencyUpdate();

        assertSame(npc, npcService.getNpcByEntity(entity).orElseThrow());
        assertSame(npc, npcService.getNpcByIdentifier("merchant").orElseThrow());
        assertTrue(npcService.getNpcByEntity(this.entity(Entity.class)).isEmpty());
        assertTrue(npcService.getNpcByIdentifier("missing").isEmpty());

        final Entity stampedEntity = this.entity(Entity.class);
        stampedEntity.getPersistentDataContainer().set(Npc.NPC_KEY, PersistentDataType.STRING, "merchant");
        assertSame(npc, npcService.getNpcByEntity(stampedEntity).orElseThrow());
    }

    @Test
    void aLiveDependencyUpdateAddsReplacesAndRemovesIdentifierMappings() {
        final Npc<Entity> firstNpc = this.npc(Entity.class, "first");
        final Npc<Entity> replacementNpc = this.npc(Entity.class, "first");
        final Npc<Entity> secondNpc = this.npc(Entity.class, "second");
        final List<Npc<?>> npcList = new ArrayList<>(List.of(firstNpc));
        final NpcService npcService = new NpcService(npcList);
        npcService.onLiveDependencyUpdate();
        assertSame(firstNpc, npcService.getNpcByIdentifier("first").orElseThrow());

        npcList.clear();
        npcList.addAll(List.of(replacementNpc, secondNpc));
        npcService.onLiveDependencyUpdate();
        assertSame(replacementNpc, npcService.getNpcByIdentifier("first").orElseThrow());
        assertSame(secondNpc, npcService.getNpcByIdentifier("second").orElseThrow());

        npcList.remove(replacementNpc);
        npcService.onLiveDependencyUpdate();
        assertTrue(npcService.getNpcByIdentifier("first").isEmpty());
        assertSame(secondNpc, npcService.getNpcByIdentifier("second").orElseThrow());
    }

    @Test
    void duplicateIdentifiersResolveToTheLastDefinitionEvenForAnEntitySpawnedByTheFirst() {
        final Npc<Entity> firstNpc = this.npc(Entity.class, "shared");
        final Npc<Entity> secondNpc = this.npc(Entity.class, "shared");
        final Entity firstEntity = this.entity(Entity.class);
        this.entityQueue.add(firstEntity);
        firstNpc.spawn(this.location(0.0D, 0.0D));
        final NpcService npcService = new NpcService(List.of(firstNpc, secondNpc));
        npcService.onLiveDependencyUpdate();

        assertSame(secondNpc, npcService.getNpcByIdentifier("shared").orElseThrow());
        assertSame(secondNpc, npcService.getNpcByEntity(firstEntity).orElseThrow());
        assertTrue(secondNpc.getNpcEntityById(firstEntity.getUniqueId()).isEmpty());
        assertEquals(2, npcService.getNpcList().size());
    }

    @Test
    void loadUnloadAndDiscardDelegateTheMatchingChunkIdentifierAndLastLocation() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        final Entity entity = this.entity(Entity.class);
        this.entityQueue.add(entity);
        npc.spawn(this.location(0.0D, 0.0D));
        final NpcService npcService = new NpcService(List.of(npc));
        npcService.onLiveDependencyUpdate();
        npcService.unload(entity);

        verify(npc).unload(entity.getUniqueId(), entity.getLocation());
        final org.bukkit.Chunk chunk = this.chunk(this.world, 1, 0);
        npcService.load(chunk);
        verify(npc).load(chunk);

        npcService.discard(entity);
        verify(npc).discard(entity.getUniqueId());
    }

    @Test
    void deathWithoutRespawnCapabilityDiscardsImmediatelyWithoutRemovingTheEntity() {
        final Npc<Entity> npc = this.npc(Entity.class, "decoration");
        final Entity entity = this.entity(Entity.class);
        this.entityQueue.add(entity);
        npc.spawn(this.location(0.0D, 0.0D));
        final NpcService npcService = new NpcService(List.of(npc));
        npcService.onLiveDependencyUpdate();

        try (final MockedStatic<UtilTask> utilTaskMockedStatic = mockStatic(UtilTask.class)) {
            npcService.death(entity);

            assertTrue(npc.getEntities().isEmpty());
            verify(npc).discard(entity.getUniqueId());
            verify(entity, never()).remove();
            utilTaskMockedStatic.verifyNoInteractions();
        }
    }

    @Test
    void respawnableDeathSchedulesTheConfiguredDelayAndRespawnsOnlyWhenTheCallbackRuns() {
        final Npc<Entity> npc = this.npc(Entity.class, "guard", RespawnableNpc.class);
        doReturn(7).when((RespawnableNpc) npc).getRespawnDelay();
        doReturn(ChronoUnit.SECONDS).when((RespawnableNpc) npc).getRespawnInterval();
        final Entity originalEntity = this.entity(Entity.class);
        final Entity respawnedEntity = this.entity(Entity.class);
        this.entityQueue.addAll(List.of(originalEntity, respawnedEntity));
        npc.spawn(this.location(0.0D, 0.0D));
        final NpcService npcService = new NpcService(List.of(npc));
        npcService.onLiveDependencyUpdate();

        try (final MockedStatic<UtilTask> utilTaskMockedStatic = mockStatic(UtilTask.class)) {
            npcService.death(originalEntity);
            final ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
            utilTaskMockedStatic.verify(() -> UtilTask.executeLaterSynchronously(
                    eq(7), eq(ChronoUnit.SECONDS), runnableArgumentCaptor.capture()
            ));
            verify(npc, never()).respawn(any());
            assertEquals(List.of(originalEntity), npc.getEntities());

            runnableArgumentCaptor.getValue().run();
            verify(npc).respawn(originalEntity.getUniqueId());
            assertEquals(List.of(respawnedEntity), npc.getEntities());
            assertTrue(npc.getEntityById(originalEntity.getUniqueId()).isEmpty());
        }
    }

    @Test
    void deathOfAnUnknownEntityDoesNotScheduleWork() {
        try (final MockedStatic<UtilTask> utilTaskMockedStatic = mockStatic(UtilTask.class)) {
            new NpcService(List.of()).death(this.entity(Entity.class));

            utilTaskMockedStatic.verifyNoInteractions();
        }
    }

    @Test
    void componentShutdownPurgesAllDefinitionsIncludingTheirPendingRecords() throws ReflectiveOperationException {
        final Npc<Entity> firstNpc = this.npc(Entity.class, "first");
        final Npc<Entity> secondNpc = this.npc(Entity.class, "second");
        final Entity liveEntity = this.entity(Entity.class);
        final Entity pendingEntity = this.entity(Entity.class);
        this.entityQueue.addAll(List.of(liveEntity, pendingEntity));
        firstNpc.spawn(this.location(0.0D, 0.0D));
        secondNpc.spawn(this.location(0.0D, 0.0D));
        secondNpc.unload(pendingEntity.getUniqueId(), this.location(0.0D, 0.0D));
        new NpcService(List.of(firstNpc, secondNpc)).onComponentShutdown();

        assertTrue(firstNpc.getEntities().isEmpty());
        assertTrue(secondNpc.getEntities().isEmpty());
        assertTrue(this.<List<?>>field(secondNpc, Npc.class, "pendingList").isEmpty());
        verify(liveEntity).remove();
        verify(firstNpc).purgeAll();
        verify(secondNpc).purgeAll();
    }

    @Test
    void searchSupportsCaseInsensitiveExactMatchesPartialMatchesAmbiguityAndPredicates() {
        final Npc<Entity> merchantNpc = this.npc(Entity.class, "Merchant");
        final Npc<Entity> guardNpc = this.npc(Entity.class, "GardenGuard");
        final Npc<Entity> gardenNpc = this.npc(Entity.class, "Gardener");
        final NpcService npcService = new NpcService(List.of(merchantNpc, guardNpc, gardenNpc));
        final CommandSender commandSender = mock(CommandSender.class);

        assertSame(merchantNpc, npcService.searchNpc(commandSender, "mErChAnT", false).orElseThrow());
        assertSame(merchantNpc, npcService.searchNpc(commandSender, "CHANT", false).orElseThrow());
        assertTrue(npcService.searchNpc(commandSender, "gar", false).isEmpty());
        assertSame(guardNpc, npcService.searchNpc(commandSender, "gar", false, npc -> npc == guardNpc).orElseThrow());
        assertTrue(npcService.searchNpc(commandSender, "missing", false).isEmpty());
        verifyNoInteractions(commandSender);
    }

    @Test
    void anExistingSearchEngineObservesSubsequentDependencyListChanges() {
        final List<Npc<?>> npcList = new ArrayList<>();
        final NpcService npcService = new NpcService(npcList);
        final NpcSearchEngine npcSearchEngine = new NpcSearchEngine(npcService);
        final CommandSender commandSender = mock(CommandSender.class);
        assertTrue(npcSearchEngine.find(commandSender, "merchant", false, null).isEmpty());

        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        npcList.add(npc);
        assertSame(npc, npcSearchEngine.find(commandSender, "merchant", false, null).orElseThrow());

        npcList.clear();
        assertTrue(npcSearchEngine.find(commandSender, "merchant", false, null).isEmpty());
    }

    @Test
    void addonExposesItsRegistrationKey() {
        assertEquals("npc", new NpcAddon().getKey());
    }
}
