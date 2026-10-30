package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class BlockingDamageListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({"true,3,3,7", "true,30,10,0", "true,-1,0,10", "false,3,0,10"})
    void resolvesClampsAndSubtractsOnlyWhenAttackCanBeBlocked(final boolean canBlock, final float resolved, final double blocked, final double remaining) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final DamageSource damageSource = mock(DamageSource.class);
        when(livingEntity.canBlockAttack(damageSource, 10.0F)).thenReturn(canBlock);
        when(livingEntity.resolveBlockedDamage(damageSource, 10.0F)).thenReturn(resolved);
        final CustomDamageEvent customDamageEvent = damage(craft(livingEntity, mock(ServerLevel.class)), null, source(damageSource), DamageCause.CUSTOM, 10.0D);
        assertDoesNotThrow(() -> new BlockingDamageListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.BLOCKING)));
        assertEquals(blocked, customDamageEvent.getBlockedDamage());
        assertEquals(remaining, customDamageEvent.getDamage());
        verify(livingEntity, times(canBlock ? 1 : 0)).resolveBlockedDamage(damageSource, 10.0F);
    }

    @Test
    void genericSourceWithoutAttackerIsSafe() {
        final org.bukkit.damage.DamageSource damageSource = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build();
        final net.minecraft.world.entity.LivingEntity livingEntity = mock(net.minecraft.world.entity.LivingEntity.class);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(net.minecraft.server.level.ServerLevel.class)),
                null,
                damageSource,
                DamageCause.CUSTOM,
                4.0D
        );

        assertNull(damageSource.getCausingEntity());
        assertNull(damageSource.getDirectEntity());
        assertDoesNotThrow(() -> new BlockingDamageListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.BLOCKING)));
    }
}