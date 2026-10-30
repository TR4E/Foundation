package me.trae.foundation.minecraft.paper.addon.damage.listeners.delay;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import me.trae.foundation.minecraft.paper.addon.damage.enums.DelayMode;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.services.DamageService;
import me.trae.foundation.minecraft.paper.addon.damage.settings.DamageAddonSettings;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class LegacyDamageDelayListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({"STARVATION,4000", "WITHER,2000", "FREEZE,2000", "POISON,1250", "FIRE_TICK,1000", "DROWNING,1000", "ENTITY_ATTACK,500", "CUSTOM,500"})
    void causeWindowsUseVanillaDurationsAndBoundary(final DamageCause damageCause, final long duration) throws Exception {
        final LegacyDamageDelayListener legacyDamageDelayListener = this.listener();
        final Entity entity = entity();
        final CustomDamageEvent firstCustomDamageEvent = damage(entity, null, mock(DamageSource.class), damageCause, 2.0D);
        atTime(firstCustomDamageEvent, 10000L);
        this.apply(legacyDamageDelayListener, firstCustomDamageEvent);
        final CustomDamageEvent insideCustomDamageEvent = damage(entity, null, mock(DamageSource.class), damageCause, 2.0D);
        atTime(insideCustomDamageEvent, 10000L + duration - 1L);
        legacyDamageDelayListener.onCustomDamage(insideCustomDamageEvent);
        assertTrue(insideCustomDamageEvent.isCancelled());

        final CustomDamageEvent boundaryCustomDamageEvent = damage(entity, null, mock(DamageSource.class), damageCause, 2.0D);
        atTime(boundaryCustomDamageEvent, 10000L + duration);
        legacyDamageDelayListener.onCustomDamage(boundaryCustomDamageEvent);
        assertFalse(boundaryCustomDamageEvent.isCancelled());
        final CustomDamageEvent otherCauseCustomDamageEvent = damage(entity, null, mock(DamageSource.class), DamageCause.FALL, 2.0D);
        atTime(otherCauseCustomDamageEvent, 10001L);
        legacyDamageDelayListener.onCustomDamage(otherCauseCustomDamageEvent);
        assertFalse(otherCauseCustomDamageEvent.isCancelled());
    }

    @Test
    void damagerWindowsAreIndependentForEachVictimAndAttacker() throws Exception {
        final LegacyDamageDelayListener legacyDamageDelayListener = this.listener();
        final Entity victimEntity = entity();
        final Entity attackerEntity = entity();
        final CustomDamageEvent firstCustomDamageEvent = damage(victimEntity, attackerEntity, mock(DamageSource.class), DamageCause.ENTITY_ATTACK, 2.0D);
        atTime(firstCustomDamageEvent, 10000L);
        this.apply(legacyDamageDelayListener, firstCustomDamageEvent);
        final CustomDamageEvent samePairCustomDamageEvent = damage(victimEntity, attackerEntity, mock(DamageSource.class), DamageCause.FIRE, 2.0D);
        atTime(samePairCustomDamageEvent, 10001L);
        legacyDamageDelayListener.onCustomDamage(samePairCustomDamageEvent);
        assertTrue(samePairCustomDamageEvent.isCancelled());

        final CustomDamageEvent otherAttackerCustomDamageEvent = damage(victimEntity, entity(), mock(DamageSource.class), DamageCause.ENTITY_ATTACK, 2.0D);
        atTime(otherAttackerCustomDamageEvent, 10001L);
        legacyDamageDelayListener.onCustomDamage(otherAttackerCustomDamageEvent);
        assertFalse(otherAttackerCustomDamageEvent.isCancelled());
        final CustomDamageEvent otherVictimCustomDamageEvent = damage(entity(), attackerEntity, mock(DamageSource.class), DamageCause.ENTITY_ATTACK, 2.0D);
        atTime(otherVictimCustomDamageEvent, 10001L);
        legacyDamageDelayListener.onCustomDamage(otherVictimCustomDamageEvent);
        assertFalse(otherVictimCustomDamageEvent.isCancelled());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void noDamageDropsPendingStateWithoutStartingAWindow(final boolean withDamager) throws Exception {
        final LegacyDamageDelayListener legacyDamageDelayListener = this.listener();
        final CustomDamageEvent customDamageEvent = damage(entity(), withDamager ? entity() : null, mock(DamageSource.class), DamageCause.CUSTOM, 0.0D);
        this.apply(legacyDamageDelayListener, customDamageEvent);

        assertTrue(((Map<?, ?>) field(legacyDamageDelayListener, "stateMap")).isEmpty());
        assertTrue(((Map<?, ?>) field(legacyDamageDelayListener, "causeMap")).isEmpty());
        assertTrue(((Map<?, ?>) field(legacyDamageDelayListener, "damagerMap")).isEmpty());
    }

    @Test
    void removalCleansVictimAndAttackerReferencesFromBothMaps() throws Exception {
        final LegacyDamageDelayListener legacyDamageDelayListener = this.listener();
        final Entity victimEntity = entity();
        final Entity attackerEntity = entity();
        this.apply(legacyDamageDelayListener, damage(victimEntity, null, mock(DamageSource.class), DamageCause.CUSTOM, 2.0D));
        this.apply(legacyDamageDelayListener, damage(victimEntity, attackerEntity, mock(DamageSource.class), DamageCause.ENTITY_ATTACK, 2.0D));
        this.apply(legacyDamageDelayListener, damage(attackerEntity, victimEntity, mock(DamageSource.class), DamageCause.ENTITY_ATTACK, 2.0D));
        legacyDamageDelayListener.onCustomDamage(damage(entity(), victimEntity, mock(DamageSource.class), DamageCause.CUSTOM, 2.0D));
        final EntityRemoveFromWorldEvent entityRemoveFromWorldEvent = mock(EntityRemoveFromWorldEvent.class);
        when(entityRemoveFromWorldEvent.getEntity()).thenReturn(victimEntity);
        legacyDamageDelayListener.onEntityRemove(entityRemoveFromWorldEvent);

        assertTrue(((Map<?, ?>) field(legacyDamageDelayListener, "causeMap")).isEmpty());
        assertTrue(((Map<?, ?>) field(legacyDamageDelayListener, "damagerMap")).isEmpty());
        assertTrue(((Map<?, ?>) field(legacyDamageDelayListener, "stateMap")).isEmpty());
    }

    private LegacyDamageDelayListener listener() {
        final DamageAddonSettings damageAddonSettings = mock(DamageAddonSettings.class);
        when(damageAddonSettings.getDelayMode()).thenReturn(DelayMode.LEGACY);

        return new LegacyDamageDelayListener(new DamageService(damageAddonSettings));
    }

    private void apply(final LegacyDamageDelayListener legacyDamageDelayListener, final CustomDamageEvent customDamageEvent) {
        legacyDamageDelayListener.onCustomDamage(customDamageEvent);
        legacyDamageDelayListener.onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.DELAY));
        legacyDamageDelayListener.onDamageApplied(new DamageAppliedEvent(customDamageEvent));
    }
}