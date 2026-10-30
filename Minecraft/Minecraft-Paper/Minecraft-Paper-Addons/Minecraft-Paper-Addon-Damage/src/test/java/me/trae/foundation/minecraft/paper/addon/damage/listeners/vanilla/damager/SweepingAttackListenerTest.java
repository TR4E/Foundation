package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackSnapshotListener.AttackSnapshot;
import me.trae.foundation.minecraft.paper.addon.damage.services.DamageService;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.craftbukkit.damage.CraftDamageType;
import org.bukkit.craftbukkit.entity.CraftArmorStand;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class SweepingAttackListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {"applied", "cancelled", "blocked"})
    void sweepUsesPrimaryBoxFiltersTargetsAndHurtsBeforeKnockbackThenEmitsOneSoundAndParticle(final String outcome) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final ServerPlayer attackerServerPlayer = mock(ServerPlayer.class);
        final CraftPlayer craftPlayer = player(attackerServerPlayer, serverLevel);
        final LivingEntity primaryLivingEntity = mock(LivingEntity.class);
        final LivingEntity allyLivingEntity = mock(LivingEntity.class);
        final LivingEntity boundaryLivingEntity = mock(LivingEntity.class);
        final LivingEntity distantLivingEntity = mock(LivingEntity.class);
        final LivingEntity markerLivingEntity = mock(LivingEntity.class);
        final LivingEntity firstLivingEntity = mock(LivingEntity.class);
        final LivingEntity secondLivingEntity = mock(LivingEntity.class);
        when(attackerServerPlayer.isAlliedTo(allyLivingEntity)).thenReturn(true);
        when(attackerServerPlayer.distanceToSqr(boundaryLivingEntity)).thenReturn(9.0D);
        when(attackerServerPlayer.distanceToSqr(distantLivingEntity)).thenReturn(10.0D);
        final CraftArmorStand craftArmorStand = mock(CraftArmorStand.class);
        when(craftArmorStand.isMarker()).thenReturn(true);
        when(markerLivingEntity.getBukkitEntity()).thenReturn(craftArmorStand);
        final org.bukkit.craftbukkit.entity.CraftLivingEntity firstEntityCraftLivingEntity = craft(firstLivingEntity, serverLevel);
        final org.bukkit.craftbukkit.entity.CraftLivingEntity secondEntityCraftLivingEntity = craft(secondLivingEntity, serverLevel);
        when(firstLivingEntity.getBukkitEntity()).thenReturn(firstEntityCraftLivingEntity);
        when(secondLivingEntity.getBukkitEntity()).thenReturn(secondEntityCraftLivingEntity);
        final AABB primaryBoxAABB = new AABB(20.0D, 30.0D, 40.0D, 21.0D, 32.0D, 41.0D);
        when(primaryLivingEntity.getBoundingBox()).thenReturn(primaryBoxAABB);
        when(serverLevel.getEntitiesOfClass(eq(LivingEntity.class), any(AABB.class))).thenReturn(List.of(
                attackerServerPlayer,
                primaryLivingEntity,
                allyLivingEntity,
                boundaryLivingEntity,
                distantLivingEntity,
                markerLivingEntity,
                firstLivingEntity,
                secondLivingEntity
        ));
        when(attackerServerPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE)).thenReturn(8.0D);
        when(attackerServerPlayer.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO)).thenReturn(0.5D);
        when(attackerServerPlayer.getYRot()).thenReturn(90.0F);
        final DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.knownCause(DamageCause.ENTITY_SWEEP_ATTACK)).thenReturn(damageSource);
        when(damageSource.typeHolder()).thenReturn(CraftDamageType.bukkitToMinecraftHolder(org.bukkit.damage.DamageType.GENERIC));
        final ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(Material.DIAMOND_SWORD);
        tag("swords", Material.DIAMOND_SWORD);
        final net.minecraft.world.item.ItemStack nmsItemStack = mock(net.minecraft.world.item.ItemStack.class);
        final CustomDamageEvent customDamageEvent = damage(
                craft(primaryLivingEntity, serverLevel),
                craftPlayer,
                null,
                source(damageSource),
                DamageCause.ENTITY_ATTACK,
                8.0D,
                itemStack,
                null
        );
        final AttackSnapshotListener attackSnapshotListener = mock(AttackSnapshotListener.class);
        when(attackSnapshotListener.getSnapshot(customDamageEvent)).thenReturn(new AttackSnapshot(0.8F, false, true, true));
        final DamageService damageService = mock(DamageService.class);
        doAnswer((final var invocation) -> {
            final CustomDamageEvent sweepingCustomDamageEvent = invocation.getArgument(0);
            sweepingCustomDamageEvent.setCancelled(outcome.equals("cancelled"));
            sweepingCustomDamageEvent.setBlockedDamage(outcome.equals("blocked") ? 1.0D : 0.0D);
            new AttackEnchantmentListener().onDamageApplied(new DamageAppliedEvent(sweepingCustomDamageEvent));

            return null;
        }).when(damageService).process(any(CustomDamageEvent.class));

        try (final MockedStatic<CraftItemStack> craftItemStackMockedStatic = mockStatic(CraftItemStack.class);
             final MockedStatic<EnchantmentHelper> enchantmentHelperMockedStatic = mockStatic(EnchantmentHelper.class)) {
            craftItemStackMockedStatic.when(() -> CraftItemStack.asNMSCopy(itemStack)).thenReturn(nmsItemStack);
            enchantmentHelperMockedStatic.when(() -> EnchantmentHelper.modifyDamage(
                    serverLevel,
                    nmsItemStack,
                    firstLivingEntity,
                    damageSource,
                    5.0F
            )).thenReturn(5.0F);
            enchantmentHelperMockedStatic.when(() -> EnchantmentHelper.modifyDamage(
                    serverLevel,
                    nmsItemStack,
                    secondLivingEntity,
                    damageSource,
                    5.0F
            )).thenReturn(5.0F);
            new SweepingAttackListener(damageService, attackSnapshotListener).onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.SWEEPING));
            enchantmentHelperMockedStatic.verify(() -> EnchantmentHelper.modifyDamage(serverLevel, nmsItemStack, firstLivingEntity, damageSource, 5.0F));
            enchantmentHelperMockedStatic.verify(() -> EnchantmentHelper.modifyDamage(serverLevel, nmsItemStack, secondLivingEntity, damageSource, 5.0F));
            enchantmentHelperMockedStatic.verify(
                    () -> EnchantmentHelper.doPostAttackEffects(serverLevel, firstLivingEntity, damageSource),
                    times(outcome.equals("applied") ? 1 : 0)
            );
            enchantmentHelperMockedStatic.verify(
                    () -> EnchantmentHelper.doPostAttackEffects(serverLevel, secondLivingEntity, damageSource),
                    times(outcome.equals("applied") ? 1 : 0)
            );
        }

        verify(serverLevel).getEntitiesOfClass(LivingEntity.class, primaryBoxAABB.inflate(1.0D, 0.25D, 1.0D));
        final ArgumentCaptor<CustomDamageEvent> customDamageEventArgumentCaptor = ArgumentCaptor.forClass(CustomDamageEvent.class);
        final InOrder inOrder = inOrder(firstLivingEntity, secondLivingEntity, damageService, craftPlayer.getWorld(), serverLevel);
        inOrder.verify(damageService).process(customDamageEventArgumentCaptor.capture());

        if (outcome.equals("applied")) {
            inOrder.verify(firstLivingEntity).knockback(
                    eq(0.4D),
                    doubleThat((final var direction) -> Math.abs(direction - 1.0D) < 0.00001D),
                    doubleThat((final var direction) -> Math.abs(direction) < 0.00001D),
                    eq(attackerServerPlayer),
                    eq(EntityKnockbackEvent.Cause.SWEEP_ATTACK)
            );
        }

        inOrder.verify(damageService).process(customDamageEventArgumentCaptor.capture());

        if (outcome.equals("applied")) {
            inOrder.verify(secondLivingEntity).knockback(
                    eq(0.4D),
                    doubleThat((final var direction) -> Math.abs(direction - 1.0D) < 0.00001D),
                    doubleThat((final var direction) -> Math.abs(direction) < 0.00001D),
                    eq(attackerServerPlayer),
                    eq(EntityKnockbackEvent.Cause.SWEEP_ATTACK)
            );
        } else {
            verify(firstLivingEntity, never()).knockback(anyDouble(), anyDouble(), anyDouble(), any(), any());
            verify(secondLivingEntity, never()).knockback(anyDouble(), anyDouble(), anyDouble(), any(), any());
        }

        inOrder.verify(craftPlayer.getWorld()).playSound(
                any(org.bukkit.Location.class),
                eq(Sound.ENTITY_PLAYER_ATTACK_SWEEP),
                eq(SoundCategory.PLAYERS),
                eq(1.0F),
                eq(1.0F)
        );
        inOrder.verify(serverLevel).sendParticles(
                eq(ParticleTypes.SWEEP_ATTACK),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                eq(0),
                anyDouble(),
                eq(0.0D),
                anyDouble(),
                eq(0.0D)
        );
        verify(damageService, times(2)).process(any());

        for (final CustomDamageEvent sweepingCustomDamageEvent : customDamageEventArgumentCaptor.getAllValues()) {
            assertEquals(4.0D, sweepingCustomDamageEvent.getOriginalDamage(), 0.00001D);
            assertEquals(DamageCause.ENTITY_SWEEP_ATTACK, sweepingCustomDamageEvent.getCause());
        }

        verify(damageSource, times(2)).knownCause(DamageCause.ENTITY_SWEEP_ATTACK);

        assertSame(firstLivingEntity.getBukkitEntity(), customDamageEventArgumentCaptor.getAllValues().getFirst().getDamagee());
        assertSame(secondLivingEntity.getBukkitEntity(), customDamageEventArgumentCaptor.getAllValues().getLast().getDamagee());

        for (final LivingEntity skipped : List.of(
                attackerServerPlayer,
                primaryLivingEntity,
                allyLivingEntity,
                boundaryLivingEntity,
                distantLivingEntity,
                markerLivingEntity
        )) {
            verify(skipped, never()).knockback(anyDouble(), anyDouble(), anyDouble(), any(), any(EntityKnockbackEvent.Cause.class));
        }
    }

    @Test
    void sweepReentryDoesNotEnumerateOrDispatchMoreTargets() {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final DamageService damageService = mock(DamageService.class);
        final CustomDamageEvent customDamageEvent = damage(
                craft(mock(LivingEntity.class), serverLevel),
                player(mock(ServerPlayer.class), serverLevel),
                source(mock(DamageSource.class)),
                DamageCause.ENTITY_SWEEP_ATTACK,
                4.0D
        );
        final AttackSnapshotListener attackSnapshotListener = mock(AttackSnapshotListener.class);
        new SweepingAttackListener(damageService, attackSnapshotListener).onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.SWEEPING));

        verifyNoInteractions(damageService, attackSnapshotListener);
        verify(serverLevel, never()).getEntitiesOfClass(any(), any(AABB.class));
    }
}