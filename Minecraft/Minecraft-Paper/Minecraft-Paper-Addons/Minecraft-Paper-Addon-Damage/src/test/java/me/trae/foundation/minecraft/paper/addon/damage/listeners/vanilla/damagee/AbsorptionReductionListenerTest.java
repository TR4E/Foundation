package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class AbsorptionReductionListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({"3,10,3,7", "20,10,10,0", "0,10,0,10"})
    void recordsReductionThenDrainsOnlyOnApplication(final double absorption, final double amount, final double absorbed, final double remaining) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final AtomicReference<Double> absorptionAmountAtomicReference = new AtomicReference<>(absorption);
        when(livingEntity.getAbsorptionAmount()).thenAnswer(_ -> absorptionAmountAtomicReference.get());
        doAnswer((final var invocation) -> {
            absorptionAmountAtomicReference.set(invocation.getArgument(0));

            return null;
        }).when(livingEntity).setAbsorptionAmount(anyDouble());
        final CustomDamageEvent customDamageEvent = damage(livingEntity, null, mock(DamageSource.class), DamageCause.CUSTOM, amount);
        final AbsorptionReductionListener absorptionReductionListener = new AbsorptionReductionListener();
        assertDoesNotThrow(() -> absorptionReductionListener.onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.ABSORPTION)));
        assertEquals(absorbed, customDamageEvent.getAbsorbedDamage());
        assertEquals(remaining, customDamageEvent.getDamage());
        assertEquals(absorption, absorptionAmountAtomicReference.get());

        absorptionReductionListener.onDamageApplied(new DamageAppliedEvent(customDamageEvent));
        assertEquals(absorption - absorbed, absorptionAmountAtomicReference.get());
        assertEquals(absorbed > 0.0D, customDamageEvent.isApplied());
        absorptionReductionListener.onDamageApplied(new DamageAppliedEvent(customDamageEvent));
        assertEquals(absorption - absorbed, absorptionAmountAtomicReference.get());
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
        assertDoesNotThrow(() -> new AbsorptionReductionListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.ABSORPTION)));
    }
}