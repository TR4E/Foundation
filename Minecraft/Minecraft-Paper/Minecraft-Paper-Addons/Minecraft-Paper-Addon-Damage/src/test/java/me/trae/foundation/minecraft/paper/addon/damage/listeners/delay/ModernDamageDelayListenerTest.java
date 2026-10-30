package me.trae.foundation.minecraft.paper.addon.damage.listeners.delay;

import me.trae.foundation.minecraft.paper.addon.damage.enums.DelayMode;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.services.DamageService;
import me.trae.foundation.minecraft.paper.addon.damage.settings.DamageAddonSettings;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class ModernDamageDelayListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({"4,false,true,4,false", "5,false,true,5,false", "8,false,false,3,false", "4,true,false,4,true"})
    void cooldownRejectsEqualOrLowerAndUsesDifferenceForHigherUnlessBypassed(
            final double incoming,
            final boolean bypass,
            final boolean cancelled,
            final double remaining,
            final boolean full
    ) {
        final DamageAddonSettings damageAddonSettings = mock(DamageAddonSettings.class);
        when(damageAddonSettings.getDelayMode()).thenReturn(DelayMode.MODERN);
        final ModernDamageDelayListener modernDamageDelayListener = new ModernDamageDelayListener(new DamageService(damageAddonSettings));
        final CraftLivingEntity craftLivingEntity = craft(mock(LivingEntity.class), mock(ServerLevel.class));
        when(craftLivingEntity.getMaximumNoDamageTicks()).thenReturn(20);
        when(craftLivingEntity.getNoDamageTicks()).thenReturn(15);
        when(craftLivingEntity.getLastDamage()).thenReturn(5.0D);
        final DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.is(DamageTypeTags.BYPASSES_COOLDOWN)).thenReturn(bypass);
        final CustomDamageEvent customDamageEvent = damage(craftLivingEntity, null, source(damageSource), DamageCause.CUSTOM, incoming);
        customDamageEvent.setFullDamage(false);
        modernDamageDelayListener.onCustomDamage(customDamageEvent);
        modernDamageDelayListener.onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.DELAY));

        assertEquals(cancelled, customDamageEvent.isCancelled());
        assertEquals(remaining, customDamageEvent.getDamage());
        assertEquals(full, customDamageEvent.isFullDamage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void applicationStoresIncomingMinusBlockedAndResetsTicksOnlyForFullHit(final boolean full) {
        final DamageAddonSettings damageAddonSettings = mock(DamageAddonSettings.class);
        when(damageAddonSettings.getDelayMode()).thenReturn(DelayMode.MODERN);
        final ModernDamageDelayListener modernDamageDelayListener = new ModernDamageDelayListener(new DamageService(damageAddonSettings));
        final CraftLivingEntity craftLivingEntity = craft(mock(LivingEntity.class), mock(ServerLevel.class));
        when(craftLivingEntity.getMaximumNoDamageTicks()).thenReturn(20);
        when(craftLivingEntity.getNoDamageTicks()).thenReturn(full ? 0 : 15);
        when(craftLivingEntity.getLastDamage()).thenReturn(5.0D);
        final CustomDamageEvent customDamageEvent = damage(craftLivingEntity, null, source(mock(DamageSource.class)), DamageCause.CUSTOM, 8.0D);
        modernDamageDelayListener.onCustomDamage(customDamageEvent);
        modernDamageDelayListener.onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.DELAY));
        customDamageEvent.setBlockedDamage(2.0D);
        customDamageEvent.setDamage(1.0D);
        modernDamageDelayListener.onDamageApplied(new DamageAppliedEvent(customDamageEvent));

        assertEquals(full, customDamageEvent.isFullDamage());
        verify(craftLivingEntity).setLastDamage(6.0D);
        verify(craftLivingEntity, times(full ? 1 : 0)).setNoDamageTicks(20);
        modernDamageDelayListener.onDamageApplied(new DamageAppliedEvent(customDamageEvent));
        verify(craftLivingEntity).setLastDamage(6.0D);
    }
}