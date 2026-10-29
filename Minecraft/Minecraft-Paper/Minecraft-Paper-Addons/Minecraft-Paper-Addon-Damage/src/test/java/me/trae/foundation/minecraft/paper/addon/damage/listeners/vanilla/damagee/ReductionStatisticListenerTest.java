package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@org.junit.jupiter.api.Tag("VERIFIED")
class ReductionStatisticListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void resistedIsExclusiveAndAbsorbedCreditsBothPlayers(final boolean damageePlayer) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final ServerPlayer victimServerPlayer = mock(ServerPlayer.class);
        final ServerPlayer attackerServerPlayer = mock(ServerPlayer.class);
        final Entity damageeEntity = damageePlayer ? player(victimServerPlayer, serverLevel) : craft(mock(LivingEntity.class), serverLevel);
        final CustomDamageEvent customDamageEvent = damage(
                damageeEntity,
                player(attackerServerPlayer, serverLevel),
                mock(DamageSource.class),
                DamageCause.ENTITY_ATTACK,
                10.0D
        );
        customDamageEvent.setResistedDamage(3.0D);
        customDamageEvent.setAbsorbedDamage(2.0D);
        new ReductionStatisticListener().onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        verify(victimServerPlayer, times(damageePlayer ? 1 : 0)).awardStat(Stats.DAMAGE_RESISTED, 30);
        verify(attackerServerPlayer, times(damageePlayer ? 0 : 1)).awardStat(Stats.DAMAGE_DEALT_RESISTED, 30);
        verify(victimServerPlayer, times(damageePlayer ? 1 : 0)).awardStat(Stats.DAMAGE_ABSORBED, 20);
        verify(attackerServerPlayer).awardStat(Stats.DAMAGE_DEALT_ABSORBED, 20);
        verify(victimServerPlayer, never()).awardStat(org.mockito.ArgumentMatchers.eq(Stats.DAMAGE_DEALT_RESISTED), org.mockito.ArgumentMatchers.anyInt());
        verify(attackerServerPlayer, never()).awardStat(org.mockito.ArgumentMatchers.eq(Stats.DAMAGE_RESISTED), org.mockito.ArgumentMatchers.anyInt());
    }
}
