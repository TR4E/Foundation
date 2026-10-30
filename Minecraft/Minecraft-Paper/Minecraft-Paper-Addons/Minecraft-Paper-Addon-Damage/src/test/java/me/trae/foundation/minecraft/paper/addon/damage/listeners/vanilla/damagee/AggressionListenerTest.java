package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class AggressionListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {"ordinary", "noAnger", "immuneWind", "nonimmuneWind"})
    void creditsMobUnlessNoAngerOrWindChargeImmunity(final String condition) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final LivingEntity damagerLivingEntity = mock(LivingEntity.class);
        final EntityType<?> entityType = mock(EntityType.class);
        doReturn(entityType).when(livingEntity).getType();
        when(entityType.is(EntityTypeTags.NO_ANGER_FROM_WIND_CHARGE)).thenReturn(condition.equals("immuneWind"));
        final DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.is(DamageTypeTags.NO_ANGER)).thenReturn(condition.equals("noAnger"));
        when(damageSource.is(DamageTypes.WIND_CHARGE)).thenReturn(condition.endsWith("Wind"));
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, serverLevel),
                craft(damagerLivingEntity, serverLevel),
                source(damageSource),
                DamageCause.CUSTOM,
                2.0D
        );
        new AggressionListener().onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        verify(livingEntity, times(condition.equals("ordinary") || condition.equals("nonimmuneWind") ? 1 : 0)).setLastHurtByMob(damagerLivingEntity);
    }

    @Test
    void creditsPlayerWithOneHundredTicksOfMemory() {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        new AggressionListener().onDamageApplied(new DamageAppliedEvent(damage(
                craft(livingEntity, serverLevel),
                player(serverPlayer, serverLevel),
                source(mock(DamageSource.class)),
                DamageCause.ENTITY_ATTACK,
                2.0D
        )));

        verify(livingEntity).setLastHurtByPlayer(serverPlayer, 100);
        verify(livingEntity).setLastHurtByMob(serverPlayer);
    }

    @Test
    void creditsTamedWolfsOwner() {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final Wolf wolf = mock(Wolf.class);
        final UUID ownerIdUUID = UUID.randomUUID();
        when(wolf.isTamed()).thenReturn(true);
        when(wolf.getOwnerUniqueId()).thenReturn(ownerIdUUID);
        new AggressionListener().onDamageApplied(new DamageAppliedEvent(damage(
                craft(livingEntity, mock(ServerLevel.class)),
                wolf,
                source(mock(DamageSource.class)),
                DamageCause.ENTITY_ATTACK,
                2.0D
        )));

        verify(livingEntity).setLastHurtByPlayer(ownerIdUUID, 100);
    }
}