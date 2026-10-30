package me.trae.foundation.minecraft.paper.addon.damage.services;

import me.trae.foundation.minecraft.paper.addon.damage.enums.DeathMode;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.settings.DamageAddonSettings;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import me.trae.foundation.minecraft.paper.addon.death.reason.Reason;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class DamageServiceTest extends DamageTestSupport {

    @Test
    void dispatchesExactStagesWithAppliedBetweenHealthAndKnockback() {
        final List<Object> dispatchList = new ArrayList<>();
        this.utilEventMock.when(() -> UtilEvent.dispatch(any(Event.class))).thenAnswer((final var invocation) -> {
            final Event event = invocation.getArgument(0);
            dispatchList.add(event instanceof final DamageSetupEvent damageSetupEvent ? damageSetupEvent.getStage() : event.getClass());

            return null;
        });
        new DamageService(mock(DamageAddonSettings.class)).process(damage(entity(), null, mock(DamageSource.class), DamageCause.CUSTOM, 10.0D));

        assertEquals(
                List.of(
                        CustomDamageEvent.class,
                        Stage.WEAPON,
                        Stage.DELAY,
                        Stage.FREEZING,
                        Stage.HARD_HAT,
                        Stage.BLOCKING,
                        Stage.ARMOUR,
                        Stage.RESISTANCE,
                        Stage.PROTECTION,
                        Stage.ABSORPTION,
                        Stage.BLOCKING_EFFECTS,
                        Stage.HEALTH,
                        DamageAppliedEvent.class,
                        Stage.KNOCKBACK,
                        Stage.SWEEPING,
                        Stage.DEATH
                ),
                dispatchList
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOM", "DELAY", "ABSORPTION", "HEALTH"})
    void cancellationStopsAtEachCheckpoint(final String checkpoint) {
        final CustomDamageEvent customDamageEvent = damage(entity(), null, mock(DamageSource.class), DamageCause.CUSTOM, 10.0D);
        final List<Object> dispatchList = new ArrayList<>();
        this.utilEventMock.when(() -> UtilEvent.dispatch(any(Event.class))).thenAnswer((final var invocation) -> {
            final Event event = invocation.getArgument(0);
            final Object stage = event instanceof final DamageSetupEvent damageSetupEvent ? damageSetupEvent.getStage() : "CUSTOM";
            dispatchList.add(stage);

            if (stage.toString().equals(checkpoint)) {
                customDamageEvent.setCancelled(true);
            }

            return null;
        });
        final DamageService damageService = new DamageService(mock(DamageAddonSettings.class));
        damageService.process(customDamageEvent);

        assertEquals(checkpoint, dispatchList.getLast().toString());
        assertEquals(switch (checkpoint) {
            case "CUSTOM" -> 1;
            case "DELAY" -> 3;
            case "ABSORPTION" -> 10;
            default -> 12;
        }, dispatchList.size());
        assertTrue(damageService.getLastDamageMap().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"NaN,3.4028234663852886E38", "Infinity,3.4028234663852886E38", "-Infinity,3.4028234663852886E38", "-2,0"})
    void clampsIncomingDamage(final double incoming, final double expected) {
        final CustomDamageEvent customDamageEvent = damage(entity(), null, mock(DamageSource.class), DamageCause.CUSTOM, incoming);
        new DamageService(mock(DamageAddonSettings.class)).process(customDamageEvent);

        assertEquals(expected, customDamageEvent.getDamage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"removed", "dead", "invulnerable", "fireResistance", "allowed", "fireWithoutResistance", "resistanceWithoutFire"})
    void checksDamageabilityBeforeDispatch(final String condition) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final net.minecraft.world.damagesource.DamageSource damageSource = mock(net.minecraft.world.damagesource.DamageSource.class);
        when(livingEntity.isRemoved()).thenReturn(condition.equals("removed"));
        when(livingEntity.isDeadOrDying()).thenReturn(condition.equals("dead"));
        when(livingEntity.isInvulnerableTo(serverLevel, damageSource)).thenReturn(condition.equals("invulnerable"));
        when(damageSource.is(DamageTypeTags.IS_FIRE)).thenReturn(condition.equals("fireResistance") || condition.equals("fireWithoutResistance"));
        when(livingEntity.hasEffect(MobEffects.FIRE_RESISTANCE)).thenReturn(condition.equals("fireResistance") || condition.equals("resistanceWithoutFire"));
        final CustomDamageEvent customDamageEvent = damage(craft(livingEntity, serverLevel), null, source(damageSource), DamageCause.CUSTOM, 10.0D);
        new DamageService(mock(DamageAddonSettings.class)).process(customDamageEvent);

        this.utilEventMock.verify(
                () -> UtilEvent.dispatch(customDamageEvent),
                times(condition.equals("allowed") || condition.endsWith("WithoutResistance") || condition.endsWith("WithoutFire") ? 1 : 0)
        );
    }

    @ParameterizedTest
    @EnumSource(DeathMode.class)
    void appliedHistoryHonoursDeathModeAndPreservesReasonedEntry(final DeathMode deathMode) {
        final DamageAddonSettings damageAddonSettings = mock(DamageAddonSettings.class);
        when(damageAddonSettings.getDeathMode()).thenReturn(deathMode);
        final DamageService damageService = new DamageService(damageAddonSettings);
        final Entity damageeEntity = entity();
        final Player player = mock(Player.class);
        final CustomDamageEvent reasonedCustomDamageEvent = damage(damageeEntity, player, mock(DamageSource.class), DamageCause.ENTITY_ATTACK, 10.0D);
        reasonedCustomDamageEvent.setReason(mock(Reason.class));
        damageService.process(reasonedCustomDamageEvent);
        assertTrue(damageService.getLastDamageMap().isEmpty());
        assertTrue(damageService.getLastPlayerDamagerDamageMap().isEmpty());

        reasonedCustomDamageEvent.setApplied(true);
        damageService.process(reasonedCustomDamageEvent);
        final CustomDamageEvent unreasonedCustomDamageEvent = damage(damageeEntity, player, mock(DamageSource.class), DamageCause.ENTITY_ATTACK, 2.0D);
        unreasonedCustomDamageEvent.setApplied(true);
        damageService.process(unreasonedCustomDamageEvent);
        final CustomDamageEvent environmentalCustomDamageEvent = damage(damageeEntity, null, mock(DamageSource.class), DamageCause.FALL, 3.0D);
        environmentalCustomDamageEvent.setApplied(true);
        damageService.process(environmentalCustomDamageEvent);

        assertSame(environmentalCustomDamageEvent, damageService.getLastDamageMap().get(damageeEntity.getUniqueId()));
        assertSame(switch (deathMode) {
            case NORMAL -> null;
            case LAST_PLAYER_DAMAGER -> unreasonedCustomDamageEvent;
            case LAST_PLAYER_DAMAGER_WITH_REASON -> reasonedCustomDamageEvent;
        }, damageService.getLastPlayerDamagerDamageMap().get(damageeEntity.getUniqueId()));
    }
}