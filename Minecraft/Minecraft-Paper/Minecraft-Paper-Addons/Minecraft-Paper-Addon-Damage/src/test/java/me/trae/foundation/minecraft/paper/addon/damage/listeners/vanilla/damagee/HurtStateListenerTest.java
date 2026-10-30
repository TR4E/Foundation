package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class HurtStateListenerTest extends DamageTestSupport {

    @Test
    void wakesAndResetsIdleTimeBeforeLaterCancellation() {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        when(livingEntity.isSleeping()).thenReturn(true);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(ServerLevel.class)),
                null,
                source(mock(DamageSource.class)),
                DamageCause.CUSTOM,
                2.0D
        );
        new HurtStateListener().onCustomDamage(customDamageEvent);
        customDamageEvent.setCancelled(true);

        verify(livingEntity).stopSleeping();
        verify(livingEntity).setNoActionTime(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void hurtAnimationTimersAreSetOnlyForFullHits(final boolean full) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(ServerLevel.class)),
                null,
                source(mock(DamageSource.class)),
                DamageCause.CUSTOM,
                2.0D
        );
        customDamageEvent.setFullDamage(full);
        new HurtStateListener().onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        assertEquals(full ? 10 : 0, livingEntity.hurtDuration);
        assertEquals(full ? 10 : 0, livingEntity.hurtTime);
    }
}