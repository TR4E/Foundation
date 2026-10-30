package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class HardHatDamageListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({"true,true,7.5", "true,false,10", "false,true,10", "false,false,10"})
    void usesNonemptySnapshotHelmetOnlyForHelmetDamage(final boolean damagesHelmet, final boolean helmetPresent, final double expected) {
        final DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.getDamageType()).thenReturn(DamageType.GENERIC);

        if (damagesHelmet) {
            tag("damages_helmet", DamageType.GENERIC);
        }

        final ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.isEmpty()).thenReturn(!helmetPresent);
        final CustomDamageEvent customDamageEvent = damage(
                entity(),
                null,
                null,
                damageSource,
                DamageCause.CUSTOM,
                10.0D,
                null,
                new ItemStack[]{null,
                        null,
                        null,
                        itemStack}
        );
        assertDoesNotThrow(() -> new HardHatDamageListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.HARD_HAT)));
        assertEquals(expected, customDamageEvent.getDamage());
    }

    @Test
    void genericSourceWithoutAttackerIsSafe() {
        final org.bukkit.damage.DamageSource damageSource = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build();
        final net.minecraft.world.entity.LivingEntity livingEntity = mock(net.minecraft.world.entity.LivingEntity.class);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(net.minecraft.server.level.ServerLevel.class)),
                null,
                damageSource,
                DamageCause.CUSTOM,
                4.0D
        );

        assertNull(damageSource.getCausingEntity());
        assertNull(damageSource.getDirectEntity());
        assertDoesNotThrow(() -> new HardHatDamageListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.HARD_HAT)));
    }
}