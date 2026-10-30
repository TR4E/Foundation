package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class ResistanceReductionListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({"0,none,8,2", "1,none,6,4", "4,none,0,10", "8,none,0,10", "1,bypasses_effects,10,0", "1,bypasses_resistance,10,0"})
    void matchesVanillaResistanceFormulaAndBypassTags(final int amplifier, final String bypass, final double remaining, final double resisted) {
        final DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.getDamageType()).thenReturn(DamageType.GENERIC);
        tag(bypass, DamageType.GENERIC);
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final PotionEffect potionEffect = mock(PotionEffect.class);
        when(potionEffect.getAmplifier()).thenReturn(amplifier);
        when(livingEntity.getPotionEffect(PotionEffectType.RESISTANCE)).thenReturn(potionEffect);
        final CustomDamageEvent customDamageEvent = damage(livingEntity, null, damageSource, DamageCause.CUSTOM, 10.0D);
        assertDoesNotThrow(() -> new ResistanceReductionListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.RESISTANCE)));
        assertEquals(remaining, customDamageEvent.getDamage());
        assertEquals(resisted, customDamageEvent.getResistedDamage());
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
        assertDoesNotThrow(() -> new ResistanceReductionListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.RESISTANCE)));
    }
}