package me.trae.foundation.minecraft.paper.addon.npc;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPostInteractEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPreInteractEvent;
import me.trae.foundation.minecraft.paper.addon.npc.listeners.NpcCleanupListener;
import me.trae.foundation.minecraft.paper.addon.npc.listeners.NpcDamageListener;
import me.trae.foundation.minecraft.paper.addon.npc.listeners.NpcInteractListener;
import me.trae.foundation.minecraft.paper.addon.npc.services.NpcService;
import me.trae.foundation.minecraft.paper.addon.npc.types.DamageableNpc;
import me.trae.foundation.minecraft.paper.addon.npc.types.InteractableNpc;
import me.trae.foundation.minecraft.paper.addon.npc.types.enums.NpcInteractType;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilTask;
import org.bukkit.Chunk;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

final class NpcListenerTest extends NpcTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void damageAndCombustionAreCancelledOnlyForProtectedNpcs(final boolean damageable) {
        final Npc<Entity> npc = damageable
                ? this.npc(Entity.class, "guard", DamageableNpc.class)
                : this.npc(Entity.class, "guard");
        final LivingEntity livingEntity = this.livingEntity();
        final NpcService npcService = mock(NpcService.class);
        when(npcService.getNpcByEntity(livingEntity)).thenReturn(Optional.of(npc));
        final NpcDamageListener npcDamageListener = new NpcDamageListener(npcService);
        final EntityDamageEvent entityDamageEvent = new EntityDamageEvent(
                livingEntity, EntityDamageEvent.DamageCause.FIRE, mock(DamageSource.class), 5.0D
        );
        final EntityCombustEvent entityCombustEvent = new EntityCombustEvent(livingEntity, 10.0F);
        npcDamageListener.onEntityDamage(entityDamageEvent);
        npcDamageListener.onEntityCombust(entityCombustEvent);

        assertEquals(!damageable, entityDamageEvent.isCancelled());
        assertEquals(!damageable, entityCombustEvent.isCancelled());
    }

    @ParameterizedTest
    @CsvSource({
            "HARMFUL,false,true", "HARMFUL,true,false",
            "BENEFICIAL,false,false", "BENEFICIAL,true,false",
            "NEUTRAL,false,false", "NEUTRAL,true,false"
    })
    void potionProtectionDependsOnCategoryAndDamageableCapability(
            final PotionEffectTypeCategory potionEffectTypeCategory,
            final boolean damageable, final boolean expectedCancelled
    ) {
        final Npc<Entity> npc = damageable
                ? this.npc(Entity.class, "guard", DamageableNpc.class)
                : this.npc(Entity.class, "guard");
        final LivingEntity livingEntity = this.livingEntity();
        final NpcService npcService = mock(NpcService.class);
        when(npcService.getNpcByEntity(livingEntity)).thenReturn(Optional.of(npc));
        final PotionEffectType potionEffectType = mock(PotionEffectType.class);
        when(potionEffectType.getCategory()).thenReturn(potionEffectTypeCategory);
        final EntityPotionEffectEvent entityPotionEffectEvent = new EntityPotionEffectEvent(
                livingEntity, null, new PotionEffect(potionEffectType, 100, 0),
                EntityPotionEffectEvent.Cause.PLUGIN, EntityPotionEffectEvent.Action.ADDED, false
        );
        new NpcDamageListener(npcService).onEntityPotionEffect(entityPotionEffectEvent);

        assertEquals(expectedCancelled, entityPotionEffectEvent.isCancelled());
    }

    @Test
    void nonNpcDamageAndCombustionAreAllowed() {
        final LivingEntity livingEntity = this.livingEntity();
        final NpcDamageListener npcDamageListener = new NpcDamageListener(new NpcService(List.of()));
        final EntityDamageEvent entityDamageEvent = new EntityDamageEvent(
                livingEntity, EntityDamageEvent.DamageCause.FALL, mock(DamageSource.class), 5.0D
        );
        final EntityCombustEvent entityCombustEvent = new EntityCombustEvent(livingEntity, 10.0F);
        npcDamageListener.onEntityDamage(entityDamageEvent);
        npcDamageListener.onEntityCombust(entityCombustEvent);

        assertFalse(entityDamageEvent.isCancelled());
        assertFalse(entityCombustEvent.isCancelled());
    }

    @Test
    void alreadyCancelledDamageCombustionAndPotionEventsDoNotResolveAnNpc() {
        final LivingEntity livingEntity = this.livingEntity();
        final NpcService npcService = mock(NpcService.class);
        final NpcDamageListener npcDamageListener = new NpcDamageListener(npcService);
        final EntityDamageEvent entityDamageEvent = new EntityDamageEvent(
                livingEntity, EntityDamageEvent.DamageCause.FIRE, mock(DamageSource.class), 5.0D
        );
        final EntityCombustEvent entityCombustEvent = new EntityCombustEvent(livingEntity, 10.0F);
        final EntityPotionEffectEvent entityPotionEffectEvent = new EntityPotionEffectEvent(
                livingEntity, null, new PotionEffect(PotionEffectType.POISON, 100, 0),
                EntityPotionEffectEvent.Cause.PLUGIN, EntityPotionEffectEvent.Action.ADDED, false
        );
        entityDamageEvent.setCancelled(true);
        entityCombustEvent.setCancelled(true);
        entityPotionEffectEvent.setCancelled(true);
        npcDamageListener.onEntityDamage(entityDamageEvent);
        npcDamageListener.onEntityCombust(entityCombustEvent);
        npcDamageListener.onEntityPotionEffect(entityPotionEffectEvent);

        assertTrue(entityDamageEvent.isCancelled());
        assertTrue(entityCombustEvent.isCancelled());
        assertTrue(entityPotionEffectEvent.isCancelled());
        verifyNoInteractions(npcService);
    }

    @ParameterizedTest
    @EnumSource(value = EntityPotionEffectEvent.Action.class, names = {"CHANGED", "CLEARED", "REMOVED"})
    void potionEventsOtherThanAddedAreIgnored(final EntityPotionEffectEvent.Action action) {
        final NpcService npcService = mock(NpcService.class);
        final PotionEffect potionEffect = new PotionEffect(PotionEffectType.POISON, 100, 0);
        final EntityPotionEffectEvent entityPotionEffectEvent = new EntityPotionEffectEvent(
                this.livingEntity(), potionEffect, potionEffect,
                EntityPotionEffectEvent.Cause.PLUGIN, action, false
        );
        new NpcDamageListener(npcService).onEntityPotionEffect(entityPotionEffectEvent);

        assertFalse(entityPotionEffectEvent.isCancelled());
        verifyNoInteractions(npcService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mainHandRightClickDispatchesInteractionAndCancelsTheBukkitEvent(final boolean damageable) {
        final Npc<Entity> npc = damageable
                ? this.npc(Entity.class, "merchant", InteractableNpc.class, DamageableNpc.class)
                : this.npc(Entity.class, "merchant", InteractableNpc.class);
        final Entity entity = this.track(npc);
        final Player player = this.entity(Player.class);
        final PlayerInteractEntityEvent playerInteractEntityEvent = new PlayerInteractEntityEvent(
                player,
                entity,
                EquipmentSlot.HAND
        );
        new NpcInteractListener(this.service(npc)).onInteractEntity(playerInteractEntityEvent);

        assertTrue(playerInteractEntityEvent.isCancelled());
        verify(this.interactableNpc(npc)).onInteract(player, entity, NpcInteractType.RIGHT_CLICK);
        assertEquals(List.of(NpcPreInteractEvent.class, NpcPostInteractEvent.class),
                this.eventList.stream().map(Object::getClass).toList());
        final NpcPreInteractEvent npcPreInteractEvent = (NpcPreInteractEvent) this.eventList.getFirst();
        assertSame(npc, npcPreInteractEvent.getNpc());
        assertSame(player, npcPreInteractEvent.getPlayer());
        assertSame(entity, npcPreInteractEvent.getEntity());
        assertEquals(NpcInteractType.RIGHT_CLICK, npcPreInteractEvent.getInteractType());
        final NpcPostInteractEvent npcPostInteractEvent = (NpcPostInteractEvent) this.eventList.getLast();
        assertSame(npc, npcPostInteractEvent.getNpc());
        assertSame(player, npcPostInteractEvent.getPlayer());
        assertSame(entity, npcPostInteractEvent.getEntity());
        assertEquals(NpcInteractType.RIGHT_CLICK, npcPostInteractEvent.getInteractType());
    }

    @Test
    void protectedNpcLeftClicksDispatchInteractionAndCancelTheAttack() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant", InteractableNpc.class);
        final Entity entity = this.track(npc);
        final Player player = this.entity(Player.class);
        final PrePlayerAttackEntityEvent prePlayerAttackEntityEvent = new PrePlayerAttackEntityEvent(player, entity, true);
        new NpcInteractListener(this.service(npc)).onAttackEntity(prePlayerAttackEntityEvent);

        assertTrue(prePlayerAttackEntityEvent.isCancelled());
        verify(this.interactableNpc(npc)).onInteract(player, entity, NpcInteractType.LEFT_CLICK);
    }

    @Test
    void leftClickOnDamageableNpcSkipsAllInteraction() {
        final Npc<Entity> npc = this.npc(Entity.class, "guard", InteractableNpc.class, DamageableNpc.class);
        final Entity entity = this.track(npc);
        final PrePlayerAttackEntityEvent prePlayerAttackEntityEvent = new PrePlayerAttackEntityEvent(
                this.entity(Player.class),
                entity,
                true
        );
        new NpcInteractListener(this.service(npc)).onAttackEntity(prePlayerAttackEntityEvent);

        assertFalse(prePlayerAttackEntityEvent.isCancelled());
        verify(this.interactableNpc(npc), never()).canInteract(any(), any(), any());
        verify(this.interactableNpc(npc), never()).onInteract(any(), any(), any());
        assertTrue(this.eventList.isEmpty());
    }

    @Test
    void offHandRightClicksAreIgnoredBeforeServiceResolution() {
        final NpcService npcService = mock(NpcService.class);
        final PlayerInteractEntityEvent playerInteractEntityEvent = new PlayerInteractEntityEvent(
                this.entity(Player.class), this.entity(Entity.class), EquipmentSlot.OFF_HAND
        );
        new NpcInteractListener(npcService).onInteractEntity(playerInteractEntityEvent);

        assertFalse(playerInteractEntityEvent.isCancelled());
        verifyNoInteractions(npcService);
        assertTrue(this.eventList.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(NpcInteractType.class)
    void aNonInteractableNpcDoesNotCancelEitherClickType(final NpcInteractType npcInteractType) {
        final Npc<Entity> npc = this.npc(Entity.class, "decoration");
        final Entity entity = this.track(npc);
        final Player player = this.entity(Player.class);
        final NpcInteractListener npcInteractListener = new NpcInteractListener(this.service(npc));

        if (npcInteractType == NpcInteractType.RIGHT_CLICK) {
            final PlayerInteractEntityEvent playerInteractEntityEvent = new PlayerInteractEntityEvent(player, entity);
            npcInteractListener.onInteractEntity(playerInteractEntityEvent);
            assertFalse(playerInteractEntityEvent.isCancelled());
        } else {
            final PrePlayerAttackEntityEvent prePlayerAttackEntityEvent = new PrePlayerAttackEntityEvent(player, entity, true);
            npcInteractListener.onAttackEntity(prePlayerAttackEntityEvent);
            assertFalse(prePlayerAttackEntityEvent.isCancelled());
        }

        assertTrue(this.eventList.isEmpty());
    }

    @Test
    void cancelledPreInteractSkipsCanInteractAndOnInteract() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant", InteractableNpc.class);
        final Entity entity = this.track(npc);
        this.cancelEvents(NpcPreInteractEvent.class);
        final PlayerInteractEntityEvent playerInteractEntityEvent = new PlayerInteractEntityEvent(
                this.entity(Player.class),
                entity
        );
        new NpcInteractListener(this.service(npc)).onInteractEntity(playerInteractEntityEvent);

        verify(this.interactableNpc(npc), never()).canInteract(any(), any(), any());
        verify(this.interactableNpc(npc), never()).onInteract(any(), any(), any());
        assertFalse(playerInteractEntityEvent.isCancelled());
        assertEquals(1, this.eventList.size());
    }

    @Test
    void canInteractFalseSkipsTheCallbackAndPostEvent() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant", InteractableNpc.class);
        final Entity entity = this.track(npc);
        doReturn(false).when(this.interactableNpc(npc)).canInteract(any(), any(), any());
        final PlayerInteractEntityEvent playerInteractEntityEvent = new PlayerInteractEntityEvent(
                this.entity(Player.class),
                entity
        );
        new NpcInteractListener(this.service(npc)).onInteractEntity(playerInteractEntityEvent);

        verify(this.interactableNpc(npc), never()).onInteract(any(), any(), any());
        assertFalse(playerInteractEntityEvent.isCancelled());
        assertEquals(1, this.eventList.size());
    }

    @Test
    void anIdentifierStampWithoutATrackedRecordDoesNotPermitInteraction() {
        final Npc<Entity> npc = this.npc(Entity.class, "merchant", InteractableNpc.class);
        final Entity entity = this.track(npc);
        npc.discard(entity.getUniqueId());
        final PlayerInteractEntityEvent playerInteractEntityEvent = new PlayerInteractEntityEvent(
                this.entity(Player.class),
                entity
        );
        new NpcInteractListener(this.service(npc)).onInteractEntity(playerInteractEntityEvent);

        assertFalse(playerInteractEntityEvent.isCancelled());
        verify(this.interactableNpc(npc), never()).canInteract(any(), any(), any());
        assertTrue(this.eventList.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(EntityRemoveEvent.Cause.class)
    void cleanupRoutesEveryRemovalCauseToExactlyOneServiceMethod(final EntityRemoveEvent.Cause cause) {
        final NpcService npcService = mock(NpcService.class);
        final Entity entity = this.entity(Entity.class);
        new NpcCleanupListener(npcService).onEntityRemove(new EntityRemoveEvent(entity, cause));

        switch (cause) {
            case UNLOAD -> verify(npcService).unload(entity);
            case DEATH -> verify(npcService).death(entity);
            default -> verify(npcService).discard(entity);
        }

        verifyNoMoreInteractions(npcService);
    }

    @Test
    void chunkLoadSchedulesTheMatchingChunkAndLoadsOnlyWhenTheTaskRuns() {
        final NpcService npcService = mock(NpcService.class);
        final Chunk chunk = this.chunk(this.world, 1, -2);

        try (final MockedStatic<UtilTask> utilTaskMockedStatic = mockStatic(UtilTask.class)) {
            new NpcCleanupListener(npcService).onChunkLoad(new ChunkLoadEvent(chunk, false));
            final ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
            utilTaskMockedStatic.verify(() -> UtilTask.executeLaterSynchronously(
                    eq(50), eq(ChronoUnit.MILLIS), runnableArgumentCaptor.capture()
            ));
            verifyNoInteractions(npcService);

            runnableArgumentCaptor.getValue().run();
            verify(npcService).load(chunk);
        }
    }

    private Entity track(final Npc<Entity> npc) {
        final Entity entity = this.entity(Entity.class);
        this.entityQueue.add(entity);
        npc.spawn(this.location(0.0D, 0.0D));
        this.eventList.clear();
        return entity;
    }

    private NpcService service(final Npc<?> npc) {
        final NpcService npcService = new NpcService(List.of(npc));
        npcService.onLiveDependencyUpdate();
        return npcService;
    }

    @SuppressWarnings("unchecked")
    private InteractableNpc<Entity> interactableNpc(final Npc<Entity> npc) {
        return (InteractableNpc<Entity>) npc;
    }
}
