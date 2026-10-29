package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.gameevent.GameEvent;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class HealthApplicationListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void appliesPlayerHealthSideEffectsInVanillaOrderAndMarksOnlyHealthDrop(final boolean dropsHealth) {
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        final CraftPlayer craftPlayer = player(serverPlayer, mock(ServerLevel.class));
        final DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.getFoodExhaustion()).thenReturn(0.1F);
        final CombatTracker combatTracker = mock(CombatTracker.class);
        when(serverPlayer.getCombatTracker()).thenReturn(combatTracker);
        when(craftPlayer.getHealth()).thenReturn(20.0D);
        final AtomicReference<Float> healthAtomicReference = new AtomicReference<>(20.0F);
        when(serverPlayer.getHealth()).thenAnswer(_ -> healthAtomicReference.get());
        doAnswer((final var invocation) -> {
            if (dropsHealth) {
                healthAtomicReference.set(invocation.getArgument(0));
            }

            return null;
        }).when(serverPlayer).setHealth(anyFloat());
        final CustomDamageEvent customDamageEvent = spy(damage(craftPlayer, null, source(damageSource), DamageCause.CUSTOM, 4.0D));
        new HealthApplicationListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.HEALTH));

        final InOrder inOrder = inOrder(serverPlayer, combatTracker, customDamageEvent);
        inOrder.verify(serverPlayer).causeFoodExhaustion(0.1F, ExhaustionReason.DAMAGED);
        inOrder.verify(serverPlayer).awardStat(Stats.DAMAGE_TAKEN, 40);
        inOrder.verify(combatTracker).recordDamage(damageSource, 4.0F);
        inOrder.verify(serverPlayer).setHealth(16.0F);
        inOrder.verify(serverPlayer).gameEvent(GameEvent.ENTITY_DAMAGE);

        if (dropsHealth) {
            inOrder.verify(customDamageEvent).setApplied(true);
        } else {
            verify(customDamageEvent, never()).setApplied(true);
        }

        assertEquals(dropsHealth, customDamageEvent.isApplied());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0D, -4.0D})
    void nonpositiveDamageHasNoHealthSideEffects(final double amount) {
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        final CustomDamageEvent customDamageEvent = damage(
                player(serverPlayer, mock(ServerLevel.class)),
                null,
                source(mock(DamageSource.class)),
                DamageCause.CUSTOM,
                amount
        );
        new HealthApplicationListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.HEALTH));

        verify(serverPlayer, never()).setHealth(anyFloat());
        verify(serverPlayer, never()).awardStat(any(net.minecraft.resources.Identifier.class), anyInt());
        assertFalse(customDamageEvent.isApplied());
    }
}
