package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damager.WeaponDurabilityEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Weapon;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class WeaponDurabilityListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({"true,0,false", "false,1,false", "false,0,true"})
    void skipsCancelledBlockedAndProjectileHits(final boolean cancelled, final double blockedDamage, final boolean projectile) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final CustomDamageEvent customDamageEvent = damage(
                entity(),
                craft(livingEntity, mock(ServerLevel.class)),
                projectile ? mock(Projectile.class) : null,
                source(mock(DamageSource.class)),
                DamageCause.ENTITY_ATTACK,
                4.0D,
                null,
                null
        );
        customDamageEvent.setCancelled(cancelled);
        customDamageEvent.setBlockedDamage(blockedDamage);
        new WeaponDurabilityListener().onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        verify(livingEntity, never()).getMainHandItem();
        this.utilEventMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @EnumSource(value = DamageCause.class, names = "ENTITY_ATTACK", mode = EnumSource.Mode.EXCLUDE)
    void skipsEveryOtherDamageCause(final DamageCause damageCause) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final CustomDamageEvent customDamageEvent = damage(
                entity(), craft(livingEntity, mock(ServerLevel.class)),
                source(mock(DamageSource.class)), damageCause, 4.0D
        );
        new WeaponDurabilityListener().onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        verify(livingEntity, never()).getMainHandItem();
        this.utilEventMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @CsvSource({"false,true", "true,false", "false,false"})
    void skipsItemsWithoutWeaponComponentOrDurability(final boolean hasWeapon, final boolean damageable) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final ItemStack itemStack = mock(ItemStack.class);
        when(livingEntity.getMainHandItem()).thenReturn(itemStack);
        when(itemStack.get(DataComponents.WEAPON)).thenReturn(hasWeapon ? mock(Weapon.class) : null);
        when(itemStack.isDamageableItem()).thenReturn(damageable);
        final CustomDamageEvent customDamageEvent = damage(
                entity(), craft(livingEntity, mock(ServerLevel.class)),
                source(mock(DamageSource.class)), DamageCause.ENTITY_ATTACK, 4.0D
        );
        new WeaponDurabilityListener().onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        verify(itemStack, never()).hurtAndBreak(anyInt(), any(LivingEntity.class), any(EquipmentSlot.class));
        this.utilEventMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @CsvSource({"3,3,false", "3,7,false", "3,0,false", "3,-1,false", "3,7,true", "1,1,false", "0,0,false"})
    void weaponComponentSuppliesAmountAndPluginCanChangeOrCancelIt(
            final int componentAmount,
            final int finalAmount,
            final boolean cancelled
    ) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final ItemStack itemStack = mock(ItemStack.class);
        when(livingEntity.getMainHandItem()).thenReturn(itemStack);
        final Weapon weapon = mock(Weapon.class);
        when(itemStack.get(DataComponents.WEAPON)).thenReturn(weapon);
        when(itemStack.isDamageableItem()).thenReturn(true);
        when(weapon.itemDamagePerAttack()).thenReturn(componentAmount);
        final CustomDamageEvent customDamageEvent = damage(
                entity(), craft(livingEntity, mock(ServerLevel.class)),
                source(mock(DamageSource.class)), DamageCause.ENTITY_ATTACK, 4.0D
        );
        final org.bukkit.inventory.ItemStack bukkitItemStack = mock(org.bukkit.inventory.ItemStack.class);
        this.utilEventMock.when(() -> UtilEvent.supply(any(WeaponDurabilityEvent.class))).thenAnswer((final var invocation) -> {
            final WeaponDurabilityEvent weaponDurabilityEvent = invocation.getArgument(0);
            assertSame(customDamageEvent, weaponDurabilityEvent.getDamageEvent());
            assertSame(bukkitItemStack, weaponDurabilityEvent.getItemStack());
            assertEquals(componentAmount, weaponDurabilityEvent.getAmount());
            weaponDurabilityEvent.setAmount(finalAmount);
            weaponDurabilityEvent.setCancelled(cancelled);

            return weaponDurabilityEvent;
        });

        try (final MockedStatic<CraftItemStack> craftItemStackMockedStatic = mockStatic(CraftItemStack.class)) {
            craftItemStackMockedStatic.when(() -> CraftItemStack.asBukkitCopy(itemStack)).thenReturn(bukkitItemStack);
            new WeaponDurabilityListener().onDamageApplied(new DamageAppliedEvent(customDamageEvent));
            craftItemStackMockedStatic.verify(() -> CraftItemStack.asBukkitCopy(itemStack));
        }

        this.utilEventMock.verify(() -> UtilEvent.supply(any(WeaponDurabilityEvent.class)));

        if (cancelled || finalAmount <= 0) {
            verify(itemStack, never()).hurtAndBreak(anyInt(), any(LivingEntity.class), any(EquipmentSlot.class));
        } else {
            verify(itemStack).hurtAndBreak(finalAmount, livingEntity, EquipmentSlot.MAINHAND);
            verify(itemStack).hurtAndBreak(anyInt(), any(LivingEntity.class), any(EquipmentSlot.class));
        }
    }

    @Test
    void ignoresSweepReentryBeforeTouchingWeapon() {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final CustomDamageEvent customDamageEvent = damage(
                entity(),
                craft(livingEntity, mock(ServerLevel.class)),
                source(mock(DamageSource.class)),
                DamageCause.ENTITY_SWEEP_ATTACK,
                4.0D
        );
        new WeaponDurabilityListener().onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        verify(livingEntity, never()).getMainHandItem();
        this.utilEventMock.verifyNoInteractions();
    }
}