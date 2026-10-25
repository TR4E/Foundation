package me.trae.foundation.minecraft.paper.addon.npc;

import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPostDespawnEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPostRestoreEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPostSpawnEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPreDespawnEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPreRestoreEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPreSpawnEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcUnloadEvent;
import me.trae.foundation.minecraft.paper.addon.npc.goals.FollowPathGoal;
import me.trae.foundation.minecraft.paper.addon.npc.goals.LookAtEntityGoal;
import me.trae.foundation.minecraft.paper.addon.npc.snapshot.NpcSnapshot;
import me.trae.foundation.minecraft.paper.addon.npc.types.DamageableNpc;
import me.trae.foundation.minecraft.paper.addon.npc.types.EquippableNpc;
import me.trae.foundation.minecraft.paper.addon.npc.types.LookingNpc;
import me.trae.foundation.minecraft.paper.addon.npc.types.MovableNpc;
import me.trae.foundation.minecraft.paper.addon.npc.types.NameableNpc;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

final class NpcLifecycleTest extends NpcTestSupport {

    @Test
    void spawnTracksTheRecordStampsTheIdentifierAndDispatchesBothEvents() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        final Entity entity = this.entity(Entity.class);
        this.entityQueue.add(entity);
        final Location location = this.location(2.0D, 3.0D);
        npc.spawn(location);

        assertEquals(List.of(entity), npc.getEntities());
        assertSame(entity, npc.getNpcEntityById(entity.getUniqueId()).orElseThrow().getEntity());
        assertSame(entity, npc.getEntityById(entity.getUniqueId()).orElseThrow());
        assertEquals("merchant", entity.getPersistentDataContainer().get(Npc.NPC_KEY, PersistentDataType.STRING));
        verify(entity).setPersistent(false);
        verify(entity).setGravity(false);
        verify(entity).setInvulnerable(true);
        verify(npc).onSetup(entity);
        verify(npc).onSpawn(entity);
        assertEquals(List.of(NpcPreSpawnEvent.class, NpcPostSpawnEvent.class),
                this.eventList.stream().map(Object::getClass).toList());
        assertSame(npc, ((NpcPreSpawnEvent) this.eventList.getFirst()).getNpc());
        assertEquals(location, ((NpcPreSpawnEvent) this.eventList.getFirst()).getLocation());
        assertSame(entity, ((NpcPostSpawnEvent) this.eventList.getLast()).getEntity());
    }

    @Test
    void npcEntityCopiesBothLocationsInsteadOfRetainingTheCallerLocation() {
        final Entity entity = this.entity(Entity.class);
        final Location location = this.location(2.0D, 3.0D);
        final NpcEntity<Entity> npcEntity = new NpcEntity<>(entity, location);
        location.setX(500.0D);

        assertEquals(2.0D, npcEntity.getSpawnedAtLocation().getX());
        assertEquals(2.0D, npcEntity.getLastKnownLocation().getX());
        assertNotSame(npcEntity.getSpawnedAtLocation(), npcEntity.getLastKnownLocation());
        assertSame(entity, npcEntity.getEntity());
        assertNull(npcEntity.getSnapshot());
    }

    @Test
    void despawnRemovesTheRecordAndEntityAndDispatchesItsEvents() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        final Entity entity = this.entity(Entity.class);
        this.entityQueue.add(entity);
        npc.spawn(this.location(0.0D, 0.0D));
        this.eventList.clear();
        npc.despawn(entity.getUniqueId());

        assertTrue(npc.getEntities().isEmpty());
        assertTrue(npc.getNpcEntityById(entity.getUniqueId()).isEmpty());
        verify(entity).remove();
        verify(npc).onDespawn(entity);
        assertEquals(List.of(NpcPreDespawnEvent.class, NpcPostDespawnEvent.class),
                this.eventList.stream().map(Object::getClass).toList());
        assertSame(entity, ((NpcPreDespawnEvent) this.eventList.getFirst()).getEntity());
        assertSame(npc, ((NpcPostDespawnEvent) this.eventList.getLast()).getNpc());
    }

    @Test
    void discardDropsOnlyTheRecord() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        final Entity entity = this.entity(Entity.class);
        this.entityQueue.add(entity);
        npc.spawn(this.location(0.0D, 0.0D));
        this.eventList.clear();
        clearInvocations(entity);
        npc.discard(entity.getUniqueId());

        assertTrue(npc.getEntities().isEmpty());
        verify(entity, never()).remove();
        verify(npc, never()).onDespawn(any());
        assertTrue(this.eventList.isEmpty());
    }

    @Test
    void unloadKeepsTheSameRecordPendingUntilItsLastKnownChunkLoads() throws ReflectiveOperationException {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        final Entity originalEntity = this.entity(Entity.class);
        final Entity restoredEntity = this.entity(Entity.class);
        this.entityQueue.addAll(List.of(originalEntity, restoredEntity));
        npc.spawn(this.location(1.0D, 1.0D));
        final NpcEntity<Entity> npcEntity = npc.getNpcEntityById(originalEntity.getUniqueId()).orElseThrow();
        npc.unload(originalEntity.getUniqueId(), this.location(33.0D, -17.0D));

        assertTrue(npc.getEntities().isEmpty());
        final List<NpcEntity<Entity>> pendingList = this.field(npc, Npc.class, "pendingList");
        assertEquals(List.of(npcEntity), pendingList);
        assertEquals(this.location(33.0D, -17.0D), npcEntity.getLastKnownLocation());
        assertInstanceOf(NpcUnloadEvent.class, this.eventList.getLast());
        assertSame(originalEntity, ((NpcUnloadEvent) this.eventList.getLast()).getEntity());
        verify(originalEntity, never()).remove();

        npc.load(this.chunk(this.world, 0, 0));
        assertEquals(List.of(npcEntity), pendingList);
        assertTrue(npc.getEntities().isEmpty());

        npc.load(this.chunk(this.world, 2, -2));
        assertTrue(pendingList.isEmpty());
        assertSame(npcEntity, npc.getNpcEntityById(restoredEntity.getUniqueId()).orElseThrow());
        assertTrue(npc.getNpcEntityById(originalEntity.getUniqueId()).isEmpty());
        assertSame(restoredEntity, npcEntity.getEntity());
        assertInstanceOf(NpcPostRestoreEvent.class, this.eventList.getLast());
    }

    @Test
    void loadRestoresOnlyMatchingPendingRecords() throws ReflectiveOperationException {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        final Entity firstEntity = this.entity(Entity.class);
        final Entity secondEntity = this.entity(Entity.class);
        final Entity restoredEntity = this.entity(Entity.class);
        this.entityQueue.addAll(List.of(firstEntity, secondEntity, restoredEntity));
        npc.spawn(this.location(0.0D, 0.0D));
        npc.spawn(this.location(0.0D, 0.0D));
        npc.unload(firstEntity.getUniqueId(), this.location(20.0D, 0.0D));
        npc.unload(secondEntity.getUniqueId(), this.location(-20.0D, 0.0D));
        npc.load(this.chunk(this.world, 1, 0));

        assertEquals(List.of(restoredEntity), npc.getEntities());
        final List<NpcEntity<Entity>> pendingList = this.field(npc, Npc.class, "pendingList");
        assertEquals(1, pendingList.size());
        assertSame(secondEntity, pendingList.getFirst().getEntity());
    }

    @Test
    void purgeAllRemovesLiveEntitiesAndClearsPendingRecords() throws ReflectiveOperationException {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        final Entity liveEntity = this.entity(Entity.class);
        final Entity pendingEntity = this.entity(Entity.class);
        this.entityQueue.addAll(List.of(liveEntity, pendingEntity));
        npc.spawn(this.location(0.0D, 0.0D));
        npc.spawn(this.location(0.0D, 0.0D));
        npc.unload(pendingEntity.getUniqueId(), this.location(0.0D, 0.0D));
        npc.purgeAll();

        assertTrue(npc.getEntities().isEmpty());
        assertTrue(this.<List<?>>field(npc, Npc.class, "pendingList").isEmpty());
        verify(liveEntity).remove();
        verify(pendingEntity, never()).remove();
        npc.load(this.chunk(this.world, 0, 0));
        assertTrue(npc.getEntities().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0,0,true", "15.999,15.999,0,0,true", "16,16,1,1,true",
            "31.999,31.999,1,1,true", "32,32,2,2,true", "-0.001,-0.001,-1,-1,true",
            "-1,-1,-1,-1,true", "-16,-16,-1,-1,true", "-16.001,-16.001,-2,-2,true",
            "-17,-17,-2,-2,true", "-32,-32,-2,-2,true", "-32.001,-32.001,-3,-3,true",
            "16,0,0,0,false", "0,16,0,0,false", "-0.001,0,0,0,false",
            "0,-0.001,0,0,false", "-16.001,-16,-1,-1,false", "-16,-16.001,-1,-1,false"
    })
    void isWithinUsesFlooredBlockCoordinatesAndArithmeticChunkShifts(
            final double x, final double z, final int chunkX, final int chunkZ, final boolean expected
    ) throws ReflectiveOperationException {
        assertEquals(expected, this.isWithin(
                this.npc(Entity.class, "merchant"),
                this.location(x, z),
                this.chunk(this.world, chunkX, chunkZ)
        ));
    }

    @Test
    void isWithinRejectsIdenticalCoordinatesInAnotherWorld() throws ReflectiveOperationException {
        assertFalse(this.isWithin(
                this.npc(Entity.class, "merchant"),
                this.location(-1.0D, 16.0D),
                this.chunk(mock(World.class), -1, 1)
        ));
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void restoreUsesTheLastLocationOnlyForMovableNpcs(final boolean movable) {
        final Npc<Entity> npc = movable
                ? this.npc(Entity.class, "merchant", MovableNpc.class)
                : this.npc(Entity.class, "merchant");
        final Entity originalEntity = this.entity(Entity.class);
        final Entity restoredEntity = this.entity(Entity.class);
        this.entityQueue.addAll(List.of(originalEntity, restoredEntity));
        final Location originalLocation = this.location(2.0D, 3.0D);
        final Location lastLocation = this.location(34.0D, -17.0D);
        npc.spawn(originalLocation);
        final NpcEntity<Entity> npcEntity = npc.getNpcEntityById(originalEntity.getUniqueId()).orElseThrow();
        npc.unload(originalEntity.getUniqueId(), lastLocation);
        npc.load(this.chunk(this.world, 2, -2));

        final Location expectedLocation = movable ? lastLocation : originalLocation;
        assertEquals(expectedLocation, npcEntity.getSpawnedAtLocation());
        final NpcPreRestoreEvent npcPreRestoreEvent = (NpcPreRestoreEvent) this.eventList.get(this.eventList.size() - 2);
        assertEquals(expectedLocation, npcPreRestoreEvent.getLocation());
        assertSame(npc, npcPreRestoreEvent.getNpc());
        assertSame(restoredEntity, npcEntity.getEntity());

        if (movable) {
            assertNotSame(lastLocation, npcEntity.getSpawnedAtLocation());
        }
    }

    @Test
    void canSpawnFalseBlocksSpawn() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        doReturn(false).when(npc).canSpawn(any());
        npc.spawn(this.location(0.0D, 0.0D));

        assertTrue(npc.getEntities().isEmpty());
        verifyNoInteractions(this.world);
        assertEquals(1, this.eventList.size());
        verify(npc, never()).onSpawn(any());
    }

    @Test
    void canSpawnFalseBlocksRestoreAndRetainsThePendingRecord() throws ReflectiveOperationException {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        final Entity entity = this.entity(Entity.class);
        this.entityQueue.add(entity);
        npc.spawn(this.location(0.0D, 0.0D));
        npc.unload(entity.getUniqueId(), this.location(0.0D, 0.0D));
        doReturn(false).when(npc).canSpawn(any());
        npc.load(this.chunk(this.world, 0, 0));

        assertTrue(npc.getEntities().isEmpty());
        assertEquals(1, this.<List<?>>field(npc, Npc.class, "pendingList").size());
        assertInstanceOf(NpcPreRestoreEvent.class, this.eventList.getLast());
        verify(npc, times(1)).onSpawn(any());
    }

    @Test
    void canDespawnFalseKeepsTheEntityAndRecord() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        final Entity entity = this.entity(Entity.class);
        this.entityQueue.add(entity);
        npc.spawn(this.location(0.0D, 0.0D));
        doReturn(false).when(npc).canDespawn(entity);
        npc.despawn(entity.getUniqueId());

        assertEquals(List.of(entity), npc.getEntities());
        verify(entity, never()).remove();
        verify(npc, never()).onDespawn(any());
        assertInstanceOf(NpcPreDespawnEvent.class, this.eventList.getLast());
    }

    @Test
    void cancelledPreSpawnBlocksTheCanCheckAndCreation() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        this.cancelEvents(NpcPreSpawnEvent.class);
        npc.spawn(this.location(0.0D, 0.0D));

        verify(npc, never()).canSpawn(any());
        assertTrue(npc.getEntities().isEmpty());
        assertEquals(1, this.eventList.size());
    }

    @Test
    void cancelledPreRestoreBlocksTheCanCheckAndRetainsPending() throws ReflectiveOperationException {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        final Entity entity = this.entity(Entity.class);
        this.entityQueue.add(entity);
        npc.spawn(this.location(0.0D, 0.0D));
        npc.unload(entity.getUniqueId(), this.location(0.0D, 0.0D));
        clearInvocations(npc);
        this.cancelEvents(NpcPreRestoreEvent.class);
        npc.load(this.chunk(this.world, 0, 0));

        verify(npc, never()).canSpawn(any());
        assertTrue(npc.getEntities().isEmpty());
        assertEquals(1, this.<List<?>>field(npc, Npc.class, "pendingList").size());
        assertInstanceOf(NpcPreRestoreEvent.class, this.eventList.getLast());
    }

    @Test
    void cancelledPreDespawnBlocksTheCanCheckAndRemoval() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant");
        final Entity entity = this.entity(Entity.class);
        this.entityQueue.add(entity);
        npc.spawn(this.location(0.0D, 0.0D));
        this.cancelEvents(NpcPreDespawnEvent.class);
        npc.despawn(entity.getUniqueId());

        verify(npc, never()).canDespawn(any());
        verify(entity, never()).remove();
        assertEquals(List.of(entity), npc.getEntities());
        assertInstanceOf(NpcPreDespawnEvent.class, this.eventList.getLast());
    }

    @Test
    void restoreAppliesThenClearsTheSnapshotAndDoesNotReplayItOnTheNextRestore() {
        final Npc<LivingEntity> npc = this.npc(LivingEntity.class, "merchant");
        final LivingEntity originalLivingEntity = this.livingEntity();
        final LivingEntity firstRestoredLivingEntity = this.livingEntity();
        final LivingEntity secondRestoredLivingEntity = this.livingEntity();
        this.entityQueue.addAll(List.of(originalLivingEntity, firstRestoredLivingEntity, secondRestoredLivingEntity));
        final NpcSnapshot firstNpcSnapshot = mock(NpcSnapshot.class);
        final NpcSnapshot secondNpcSnapshot = mock(NpcSnapshot.class);
        doReturn(firstNpcSnapshot).when(npc).createSnapshot(originalLivingEntity);
        doReturn(secondNpcSnapshot).when(npc).createSnapshot(firstRestoredLivingEntity);
        npc.spawn(this.location(0.0D, 0.0D));
        final NpcEntity<LivingEntity> npcEntity = npc.getNpcEntityById(originalLivingEntity.getUniqueId()).orElseThrow();
        npc.unload(originalLivingEntity.getUniqueId(), this.location(0.0D, 0.0D));
        assertSame(firstNpcSnapshot, npcEntity.getSnapshot());

        npc.load(this.chunk(this.world, 0, 0));
        verify(firstNpcSnapshot).apply(firstRestoredLivingEntity);
        assertNull(npcEntity.getSnapshot());

        npc.unload(firstRestoredLivingEntity.getUniqueId(), this.location(0.0D, 0.0D));
        npc.load(this.chunk(this.world, 0, 0));
        verify(firstNpcSnapshot, times(1)).apply(any());
        verify(secondNpcSnapshot).apply(secondRestoredLivingEntity);
        assertNull(npcEntity.getSnapshot());
    }

    @Test
    void respawnClearsAStaleSnapshotAndUsesDefaultHealthAndEquipment() {
        final Npc<LivingEntity> npc = this.npc(LivingEntity.class, "guard", DamageableNpc.class, EquippableNpc.class);
        final DamageableNpc damageableNpc = (DamageableNpc) npc;
        doReturn(40.0D).when(damageableNpc).getMaxHealth();
        doReturn(25.0D).when(damageableNpc).getDefaultHealth();
        final ItemStack defaultItemStack = mock(ItemStack.class);
        doReturn(defaultItemStack).when((EquippableNpc) npc).getItemInMainHand();
        final LivingEntity originalLivingEntity = this.livingEntity();
        final LivingEntity respawnedLivingEntity = this.livingEntity();
        this.maxHealth(originalLivingEntity, 40.0D);
        this.maxHealth(respawnedLivingEntity, 40.0D);
        final EntityEquipment entityEquipment = mock(EntityEquipment.class);
        when(respawnedLivingEntity.getEquipment()).thenReturn(entityEquipment);
        this.entityQueue.addAll(List.of(originalLivingEntity, respawnedLivingEntity));
        npc.spawn(this.location(0.0D, 0.0D));
        final NpcEntity<LivingEntity> npcEntity = npc.getNpcEntityById(originalLivingEntity.getUniqueId()).orElseThrow();
        final NpcSnapshot npcSnapshot = mock(NpcSnapshot.class);
        npcEntity.setSnapshot(npcSnapshot);
        npc.respawn(originalLivingEntity.getUniqueId());

        assertEquals(List.of(respawnedLivingEntity), npc.getEntities());
        assertNull(npcEntity.getSnapshot());
        verifyNoInteractions(npcSnapshot);
        verify(respawnedLivingEntity).setHealth(25.0D);
        verify(entityEquipment).setItemInMainHand(defaultItemStack);
        verify(respawnedLivingEntity).setInvulnerable(false);
        verify(respawnedLivingEntity).setCollidable(true);
    }

    @Test
    void mobCapabilitiesInstallConfiguredGoalsAndNameDuringSetup() throws ReflectiveOperationException {
        final Npc<Mob> npc = this.npc(Mob.class, "guide", MovableNpc.class, LookingNpc.class, NameableNpc.class);
        final Component nameComponent = Component.text("Guide");
        doReturn(nameComponent).when((NameableNpc) npc).getName();
        final MovableNpc movableNpc = (MovableNpc) npc;
        final List<Location> locationList = List.of(this.location(10.0D, 0.0D));
        doReturn(locationList).when(movableNpc).getPathLocations();
        doReturn(4L).when(movableNpc).getPathWaitTicks();
        doReturn(2.0D).when(movableNpc).getMovementSpeed();
        doReturn(false).when(movableNpc).isPathLooping();
        final Mob mob = this.entity(Mob.class);
        this.entityQueue.add(mob);
        npc.spawn(this.location(0.0D, 0.0D));

        verify(this.mobGoals).removeAllGoals(mob);
        verify(this.mobGoals).addGoal(eq(mob), eq(0), any(LookAtEntityGoal.class));
        final ArgumentCaptor<FollowPathGoal> followPathGoalArgumentCaptor = ArgumentCaptor.forClass(FollowPathGoal.class);
        verify(this.mobGoals).addGoal(eq(mob), eq(1), followPathGoalArgumentCaptor.capture());
        final FollowPathGoal followPathGoal = followPathGoalArgumentCaptor.getValue();
        assertSame(locationList, this.field(followPathGoal, FollowPathGoal.class, "locationList"));
        assertEquals(4L, this.<Long>field(followPathGoal, FollowPathGoal.class, "waitTicks"));
        assertEquals(2.0D, this.<Double>field(followPathGoal, FollowPathGoal.class, "speed"));
        assertFalse(this.<Boolean>field(followPathGoal, FollowPathGoal.class, "looping"));
        verify(mob).setGravity(true);
        verify(mob).customName(nameComponent);
        verify(mob).setCustomNameVisible(true);
    }
}
