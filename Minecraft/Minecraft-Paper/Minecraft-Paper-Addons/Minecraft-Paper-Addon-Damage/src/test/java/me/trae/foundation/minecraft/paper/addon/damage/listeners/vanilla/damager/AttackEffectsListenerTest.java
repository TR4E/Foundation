package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackSnapshotListener.AttackSnapshot;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttackEffectsListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @Tag("UNVERIFIED")
    @CsvSource({
            "false,false,1,ENTITY_PLAYER_ATTACK_STRONG",
            "false,false,0.9,ENTITY_PLAYER_ATTACK_WEAK",
            "true,false,1,ENTITY_PLAYER_ATTACK_CRIT",
            "false,true,1,NONE"
    })
    void selectsVanillaAttackSounds(
            final boolean critical,
            final boolean sweep,
            final float strength,
            final String expectedSound
    ) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        final CraftPlayer craftPlayer = player(serverPlayer, serverLevel);
        final CustomDamageEvent customDamageEvent = damage(
                craft(mock(LivingEntity.class), serverLevel),
                craftPlayer,
                source(mock(DamageSource.class)),
                DamageCause.ENTITY_ATTACK,
                4.0D
        );
        customDamageEvent.setCritical(critical);
        final AttackSnapshotListener attackSnapshotListener = mock(AttackSnapshotListener.class);
        when(attackSnapshotListener.getSnapshot(customDamageEvent)).thenReturn(new AttackSnapshot(strength, false, true, sweep));
        new AttackEffectsListener(attackSnapshotListener).onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        if (expectedSound.equals("NONE")) {
            verify(craftPlayer.getWorld(), never()).playSound(
                    any(org.bukkit.Location.class),
                    any(Sound.class),
                    any(SoundCategory.class),
                    anyFloat(),
                    anyFloat()
            );
        } else {
            final Sound sound = switch (expectedSound) {
                case "ENTITY_PLAYER_ATTACK_STRONG" -> Sound.ENTITY_PLAYER_ATTACK_STRONG;
                case "ENTITY_PLAYER_ATTACK_WEAK" -> Sound.ENTITY_PLAYER_ATTACK_WEAK;
                case "ENTITY_PLAYER_ATTACK_CRIT" -> Sound.ENTITY_PLAYER_ATTACK_CRIT;
                default -> Sound.ENTITY_PLAYER_ATTACK_NODAMAGE;
            };
            verify(craftPlayer.getWorld()).playSound(any(org.bukkit.Location.class), eq(sound), eq(SoundCategory.PLAYERS), eq(1.0F), eq(1.0F));
        }
    }

    @Test
    @Tag("VERIFIED")
    void exhaustionAndDealtStatisticUseOriginalDamage() {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        final CraftPlayer craftPlayer = player(serverPlayer, serverLevel);
        final CustomDamageEvent customDamageEvent = damage(
                craft(mock(LivingEntity.class), serverLevel),
                craftPlayer,
                source(mock(DamageSource.class)),
                DamageCause.ENTITY_ATTACK,
                12.0D
        );
        customDamageEvent.setDamage(2.0D);
        final AttackSnapshotListener attackSnapshotListener = mock(AttackSnapshotListener.class);
        when(attackSnapshotListener.getSnapshot(customDamageEvent)).thenReturn(new AttackSnapshot(1.0F, true, true, false));
        new AttackEffectsListener(attackSnapshotListener).onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        verify(serverPlayer).causeFoodExhaustion(0.1F, ExhaustionReason.ATTACK);
        verify(serverPlayer).awardStat(Stats.DAMAGE_DEALT, 120);
        verify(serverPlayer, never()).awardStat(Stats.DAMAGE_DEALT, 20);
    }

    @ParameterizedTest
    @Tag("VERIFIED")
    @ValueSource(booleans = {true, false})
    void delayCancellationAndBlockedHitPlayNoDamageExactlyOnce(final boolean cancelledAtDelay) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        final CraftPlayer craftPlayer = player(serverPlayer, serverLevel);
        final CustomDamageEvent customDamageEvent = damage(
                entity(), craftPlayer, source(mock(DamageSource.class)), DamageCause.ENTITY_ATTACK, 4.0D
        );
        customDamageEvent.setCancelled(cancelledAtDelay);
        customDamageEvent.setBlockedDamage(cancelledAtDelay ? 0.0D : 4.0D);
        final AttackEffectsListener attackEffectsListener = new AttackEffectsListener(mock(AttackSnapshotListener.class));
        attackEffectsListener.onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.DELAY));
        attackEffectsListener.onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        verify(craftPlayer.getWorld()).playSound(
                any(org.bukkit.Location.class), eq(Sound.ENTITY_PLAYER_ATTACK_NODAMAGE),
                eq(SoundCategory.PLAYERS), eq(1.0F), eq(1.0F)
        );
        verify(serverPlayer, never()).causeFoodExhaustion(anyFloat(), any());
        verify(serverPlayer, never()).awardStat(eq(Stats.DAMAGE_DEALT), org.mockito.ArgumentMatchers.anyInt());
    }

    @ParameterizedTest
    @Tag("UNVERIFIED")
    @CsvSource({"true,1,true", "true,0.9,false", "false,1,false"})
    void reconstructedSprintSoundRequiresSprintAndStrongAttack(final boolean sprint, final float strength, final boolean expected) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final CraftPlayer craftPlayer = player(mock(ServerPlayer.class), serverLevel);
        final CustomDamageEvent customDamageEvent = damage(entity(), craftPlayer, source(mock(DamageSource.class)), DamageCause.ENTITY_ATTACK, 4.0D);
        final AttackSnapshotListener attackSnapshotListener = mock(AttackSnapshotListener.class);
        when(attackSnapshotListener.getSnapshot(customDamageEvent)).thenReturn(new AttackSnapshot(strength, sprint, true, false));
        new AttackEffectsListener(attackSnapshotListener).onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        verify(craftPlayer.getWorld(), times(expected ? 1 : 0)).playSound(
                any(org.bukkit.Location.class), eq(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK),
                eq(SoundCategory.PLAYERS), eq(1.0F), eq(1.0F)
        );
    }

    @ParameterizedTest
    @Tag("UNVERIFIED")
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void reconstructedCritAndEnchantedHitParticles(final boolean critical, final boolean enchanted) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        final CraftPlayer craftPlayer = player(serverPlayer, serverLevel);
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final DamageSource damageSource = mock(DamageSource.class);
        final org.bukkit.inventory.ItemStack itemStack = mock(org.bukkit.inventory.ItemStack.class);
        final net.minecraft.world.item.ItemStack nmsItemStack = mock(net.minecraft.world.item.ItemStack.class);
        when(serverPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE)).thenReturn(4.0D);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, serverLevel), craftPlayer, null, source(damageSource),
                DamageCause.ENTITY_ATTACK, 4.0D, itemStack, null
        );
        customDamageEvent.setCritical(critical);

        try (final MockedStatic<CraftItemStack> craftItemStackMockedStatic = mockStatic(CraftItemStack.class);
             final MockedStatic<EnchantmentHelper> enchantmentHelperMockedStatic = mockStatic(EnchantmentHelper.class)) {
            craftItemStackMockedStatic.when(() -> CraftItemStack.asNMSCopy(itemStack)).thenReturn(nmsItemStack);
            enchantmentHelperMockedStatic.when(() -> EnchantmentHelper.modifyDamage(
                    serverLevel, nmsItemStack, livingEntity, damageSource, 4.0F
            )).thenReturn(enchanted ? 6.0F : 4.0F);
            new AttackEffectsListener(mock(AttackSnapshotListener.class)).onDamageApplied(new DamageAppliedEvent(customDamageEvent));
        }

        verify(craftPlayer.getWorld(), times(critical ? 1 : 0)).spawnParticle(
                eq(Particle.CRIT), any(org.bukkit.Location.class), eq(12), eq(0.2D), eq(0.3D), eq(0.2D), eq(0.0D)
        );
        verify(craftPlayer.getWorld(), times(enchanted ? 1 : 0)).spawnParticle(
                eq(Particle.ENCHANTED_HIT), any(org.bukkit.Location.class), eq(12), eq(0.2D), eq(0.3D), eq(0.2D), eq(0.0D)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "ENTITY_ATTACK,false,true,true",
            "ENTITY_SWEEP_ATTACK,false,true,false",
            "PROJECTILE,true,true,false",
            "ENTITY_ATTACK,true,true,false",
            "ENTITY_ATTACK,false,false,false"
    })
    @Tag("VERIFIED")
    void playerAttackRequiresDirectEntityAttackByPlayer(final DamageCause damageCause, final boolean projectile, final boolean player, final boolean expected) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        final CraftPlayer craftPlayer = player(serverPlayer, serverLevel);
        final CustomDamageEvent customDamageEvent = damage(
                entity(),
                player ? craftPlayer : entity(),
                projectile ? mock(Projectile.class) : null,
                source(mock(DamageSource.class)),
                damageCause,
                4.0D,
                null,
                null
        );
        customDamageEvent.setCancelled(true);
        new AttackEffectsListener(mock(AttackSnapshotListener.class)).onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.DELAY));

        verify(craftPlayer.getWorld(), times(expected ? 1 : 0)).playSound(
                any(org.bukkit.Location.class),
                eq(Sound.ENTITY_PLAYER_ATTACK_NODAMAGE),
                eq(SoundCategory.PLAYERS),
                eq(1.0F),
                eq(1.0F)
        );
        customDamageEvent.setCancelled(false);
        new AttackEffectsListener(mock(AttackSnapshotListener.class)).onDamageApplied(new DamageAppliedEvent(customDamageEvent));
        verify(serverPlayer, times(expected ? 1 : 0)).awardStat(Stats.DAMAGE_DEALT, 40);
    }
}
