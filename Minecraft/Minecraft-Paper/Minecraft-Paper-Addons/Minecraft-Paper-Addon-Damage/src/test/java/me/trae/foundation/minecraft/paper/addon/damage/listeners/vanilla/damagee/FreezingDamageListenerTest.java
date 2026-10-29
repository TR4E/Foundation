package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class FreezingDamageListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({"true,true,50", "true,false,10", "false,true,10", "false,false,10"})
    void multipliesOnlyFreezingAgainstExtraHurtType(final boolean freezing, final boolean extraHurt, final double expected) {
        final DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.getDamageType()).thenReturn(DamageType.GENERIC);
        final Entity entity = entity();
        when(entity.getType()).thenReturn(EntityType.BLAZE);

        if (freezing) {
            tag("is_freezing", DamageType.GENERIC);
        }

        if (extraHurt) {
            tag("freeze_hurts_extra_types", EntityType.BLAZE);
        }

        final CustomDamageEvent customDamageEvent = damage(entity, null, damageSource, DamageCause.FREEZE, 10.0D);
        assertDoesNotThrow(() -> new FreezingDamageListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.FREEZING)));
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
        assertDoesNotThrow(() -> new FreezingDamageListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.FREEZING)));
    }
}
