package me.trae.foundation.minecraft.paper.addon.damage.events;

import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class CustomDamageEventTest extends DamageTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bothCustomConstructorsReplaceNullWithGeneric(final boolean withDamager) {
        final CustomDamageEvent customDamageEvent = withDamager
                ? new CustomDamageEvent(entity(), entity(), null, DamageCause.CUSTOM, 10.0D)
                : new CustomDamageEvent(entity(), null, DamageCause.CUSTOM, 10.0D);

        assertNotNull(customDamageEvent.getSource());
        assertSame(DamageType.GENERIC, customDamageEvent.getSource().getDamageType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"vanilla", "byEntity", "custom", "customWithDamager"})
    void fourConstructorsStartWithNoBlockedResistedOrAbsorbedDamage(final String constructor) {
        final Entity damageeEntity = entity();
        final Entity damagerEntity = entity();
        final DamageSource damageSource = mock(DamageSource.class);
        final EntityDamageEvent entityDamageEvent = mock(EntityDamageEvent.class);
        when(entityDamageEvent.getEntity()).thenReturn(damageeEntity);
        when(entityDamageEvent.getDamageSource()).thenReturn(damageSource);
        when(entityDamageEvent.getCause()).thenReturn(DamageCause.CUSTOM);
        when(entityDamageEvent.getDamage()).thenReturn(10.0D);
        final EntityDamageByEntityEvent entityDamageByEntityEvent = mock(EntityDamageByEntityEvent.class);
        when(entityDamageByEntityEvent.getEntity()).thenReturn(damageeEntity);
        when(entityDamageByEntityEvent.getDamager()).thenReturn(damagerEntity);
        when(entityDamageByEntityEvent.getDamageSource()).thenReturn(damageSource);
        when(entityDamageByEntityEvent.getCause()).thenReturn(DamageCause.ENTITY_ATTACK);
        when(entityDamageByEntityEvent.getDamage()).thenReturn(10.0D);
        final CustomDamageEvent customDamageEvent = switch (constructor) {
            case "vanilla" -> new CustomDamageEvent(entityDamageEvent);
            case "byEntity" -> new CustomDamageEvent(entityDamageByEntityEvent);
            case "custom" -> new CustomDamageEvent(damageeEntity, damageSource, DamageCause.CUSTOM, 10.0D);
            default -> new CustomDamageEvent(damageeEntity, damagerEntity, damageSource, DamageCause.ENTITY_ATTACK, 10.0D);
        };

        assertEquals(0.0D, customDamageEvent.getBlockedDamage());
        assertEquals(0.0D, customDamageEvent.getResistedDamage());
        assertEquals(0.0D, customDamageEvent.getAbsorbedDamage());
    }

    @Test
    void arithmeticAndFinalDamage() {
        final CustomDamageEvent customDamageEvent = damage(entity(), null, mock(DamageSource.class), DamageCause.CUSTOM, 10.0D);
        customDamageEvent.addDamage(2.0D);
        assertEquals(12.0D, customDamageEvent.getDamage());
        customDamageEvent.multiplyDamage(2.0D);
        assertEquals(24.0D, customDamageEvent.getDamage());
        customDamageEvent.subtractDamage(4.0D);
        assertEquals(20.0D, customDamageEvent.getFinalDamage());
        assertTrue(customDamageEvent.hasDamage());
        customDamageEvent.subtractDamage(30.0D);
        assertEquals(0.0D, customDamageEvent.getFinalDamage());
        assertFalse(customDamageEvent.hasDamage());
    }

    @ParameterizedTest
    @CsvSource({"add,-20", "subtract,20", "multiply,-2", "multiply,0"})
    void arithmeticCannotExposeNegativeFinalDamage(final String operation, final double operand) {
        final CustomDamageEvent customDamageEvent = damage(entity(), null, mock(DamageSource.class), DamageCause.CUSTOM, 10.0D);

        switch (operation) {
            case "add" -> customDamageEvent.addDamage(operand);
            case "subtract" -> customDamageEvent.subtractDamage(operand);
            default -> customDamageEvent.multiplyDamage(operand);
        }

        assertEquals(0.0D, customDamageEvent.getFinalDamage());
        assertFalse(customDamageEvent.hasDamage());
    }

    @ParameterizedTest
    @CsvSource({"ENTITY_ATTACK,true", "ENTITY_SWEEP_ATTACK,true", "PROJECTILE,false", "CUSTOM,false"})
    void identifiesMeleeCauses(final DamageCause damageCause, final boolean expected) {
        assertEquals(expected, damage(entity(), null, mock(DamageSource.class), damageCause, 4.0D).isMelee());
    }
}