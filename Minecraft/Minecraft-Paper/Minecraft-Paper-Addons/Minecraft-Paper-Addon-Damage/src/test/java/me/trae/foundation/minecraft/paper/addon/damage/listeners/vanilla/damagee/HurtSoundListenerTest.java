package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import me.trae.foundation.minecraft.paper.plugin.framework.stash.SoundStash;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class HurtSoundListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deadOrDyingDamageeHasNoHurtSounds(final boolean dead) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final DamageSource damageSource = mock(DamageSource.class);
        when(livingEntity.isDeadOrDying()).thenReturn(dead);
        final SoundStash soundStash = mock(SoundStash.class);
        final CustomDamageEvent customDamageEvent = damage(craft(livingEntity, mock(ServerLevel.class)), null, source(damageSource), DamageCause.CUSTOM, 2.0D);
        customDamageEvent.setSoundStash(soundStash);
        new HurtSoundListener().onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        verify(soundStash, times(dead ? 0 : 1)).play(any(org.bukkit.Location.class));
        verify(livingEntity, times(dead ? 0 : 1)).playSecondaryHurtSound(damageSource);
    }
}
