package me.trae.foundation.minecraft.paper.addon.damage.listeners;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class CustomDeathListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({
            "DEATH,true,true,false,false",
            "DEATH,true,false,false,false",
            "DEATH,true,false,true,false",
            "DEATH,true,false,false,true",
            "HEALTH,true,false,false,false",
            "DEATH,false,false,false,false"
    })
    void onlyDeathStageKillsDeadDamageeAndRestoresSilentDeath(
            final Stage stage,
            final boolean dead,
            final boolean full,
            final boolean previousSilent,
            final boolean throwsOnDeath
    ) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)).thenReturn(true);
        when(livingEntity.isDeadOrDying()).thenReturn(dead);
        livingEntity.silentDeath = previousSilent;
        final CustomDamageEvent customDamageEvent = damage(craft(livingEntity, serverLevel), null, source(damageSource), DamageCause.CUSTOM, 4.0D);
        customDamageEvent.setFullDamage(full);
        doAnswer(_ -> {
            assertEquals(previousSilent || !full, livingEntity.silentDeath);

            if (throwsOnDeath) {
                throw new IllegalStateException("death failure");
            }

            return null;
        }).when(livingEntity).die(damageSource);

        if (throwsOnDeath) {
            assertThrows(IllegalStateException.class, () -> new CustomDeathListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, stage)));
        } else {
            new CustomDeathListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, stage));
        }

        verify(livingEntity, times(stage == Stage.DEATH && dead ? 1 : 0)).die(damageSource);
        assertEquals(previousSilent, livingEntity.silentDeath);
    }

    @Test
    void missingCraftSourceFallsBackToGeneric() {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final DamageSources damageSources = mock(DamageSources.class);
        final DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)).thenReturn(true);
        when(livingEntity.isDeadOrDying()).thenReturn(true);
        when(serverLevel.damageSources()).thenReturn(damageSources);
        when(damageSources.generic()).thenReturn(damageSource);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, serverLevel),
                null,
                mock(org.bukkit.damage.DamageSource.class),
                DamageCause.CUSTOM,
                4.0D
        );
        new CustomDeathListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.DEATH));

        verify(livingEntity).die(damageSource);
        assertFalse(livingEntity.silentDeath);
    }
}
