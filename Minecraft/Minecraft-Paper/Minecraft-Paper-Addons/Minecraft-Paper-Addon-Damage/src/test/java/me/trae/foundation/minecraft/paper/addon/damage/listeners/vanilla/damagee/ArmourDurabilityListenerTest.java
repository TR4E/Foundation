package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.damage.DamageType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class ArmourDurabilityListenerTest extends DamageTestSupport {

    @Test
    void listensAtLowestPriorityOnArmourStage() throws Exception {
        assertEquals(
                EventPriority.LOWEST,
                ArmourDurabilityListener.class.getMethod("onDamageSetup", DamageSetupEvent.class).getAnnotation(EventHandler.class).priority()
        );
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        final CustomDamageEvent customDamageEvent = damage(
                player(serverPlayer, mock(ServerLevel.class)),
                null,
                source(mock(DamageSource.class)),
                DamageCause.CUSTOM,
                12.0D
        );
        new ArmourDurabilityListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.PROTECTION));

        verify(serverPlayer, never()).getItemBySlot(any());
    }

    @ParameterizedTest
    @CsvSource({"12,false,3,0", "0.5,false,1,0", "12,true,3,4"})
    void damagesEquipmentFromPreArmourDamageAndUsesUnscaledHelmetPass(
            final double amount,
            final boolean helmetSource,
            final int regularAmount,
            final int helmetAmount
    ) {
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        final DamageSource damageSource = mock(DamageSource.class);
        final ItemStack itemStack = mock(ItemStack.class);
        final Equippable equippable = mock(Equippable.class);
        when(itemStack.get(DataComponents.EQUIPPABLE)).thenReturn(equippable);
        when(equippable.damageOnHurt()).thenReturn(true);
        when(itemStack.isDamageableItem()).thenReturn(true);
        when(itemStack.canBeHurtBy(damageSource)).thenReturn(true);
        when(serverPlayer.getItemBySlot(any())).thenReturn(itemStack);

        if (helmetSource) {
            tag("damages_helmet", DamageType.GENERIC);
        }

        final CustomDamageEvent customDamageEvent = damage(
                player(serverPlayer, mock(ServerLevel.class)),
                null,
                source(damageSource),
                DamageCause.CUSTOM,
                amount
        );

        try (final MockedStatic<CraftItemStack> craftItemStackMockedStatic = mockStatic(CraftItemStack.class)) {
            craftItemStackMockedStatic.when(() -> CraftItemStack.asBukkitCopy(itemStack)).thenReturn(mock(org.bukkit.inventory.ItemStack.class));
            new ArmourDurabilityListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.ARMOUR));

            for (final EquipmentSlot equipmentSlot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
                verify(itemStack).hurtAndBreak(regularAmount, serverPlayer, equipmentSlot);
            }

            if (helmetSource) {
                verify(itemStack).hurtAndBreak(helmetAmount, serverPlayer, EquipmentSlot.HEAD);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingComponent", "noDamageOnHurt", "notDamageable", "immune"})
    void skipsPiecesFailingAnyDurabilityCondition(final String failure) {
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        final DamageSource damageSource = mock(DamageSource.class);
        final ItemStack itemStack = mock(ItemStack.class);
        final Equippable equippable = mock(Equippable.class);
        when(itemStack.get(DataComponents.EQUIPPABLE)).thenReturn(failure.equals("missingComponent") ? null : equippable);
        when(equippable.damageOnHurt()).thenReturn(!failure.equals("noDamageOnHurt"));
        when(itemStack.isDamageableItem()).thenReturn(!failure.equals("notDamageable"));
        when(itemStack.canBeHurtBy(damageSource)).thenReturn(!failure.equals("immune"));
        when(serverPlayer.getItemBySlot(any())).thenReturn(itemStack);
        final CustomDamageEvent customDamageEvent = damage(
                player(serverPlayer, mock(ServerLevel.class)),
                null,
                source(damageSource),
                DamageCause.CUSTOM,
                12.0D
        );
        new ArmourDurabilityListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.ARMOUR));

        verify(itemStack, never()).hurtAndBreak(anyInt(), eq(serverPlayer), any(EquipmentSlot.class));
    }
}