package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class HurtBroadcastListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({"true,0,false,true,true", "true,0,true,true,false", "false,0,false,false,false", "true,1,false,false,false"})
    void broadcastsOnlyFullUnblockedHitsAndMarksImpact(
            final boolean full,
            final double blocked,
            final boolean noImpact,
            final boolean broadcast,
            final boolean marked
    ) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.is(DamageTypeTags.NO_IMPACT)).thenReturn(noImpact);
        final CustomDamageEvent customDamageEvent = damage(craft(livingEntity, serverLevel), null, source(damageSource), DamageCause.CUSTOM, 2.0D);
        customDamageEvent.setFullDamage(full);
        customDamageEvent.setBlockedDamage(blocked);
        new HurtBroadcastListener().onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        verify(serverLevel, times(broadcast ? 1 : 0)).broadcastDamageEvent(livingEntity, damageSource);
        assertEquals(marked, livingEntity.hurtMarked);
    }
}