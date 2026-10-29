package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class ArmourReductionListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void usesCombatRulesWithEntityArmourAndToughnessUnlessBypassed(final boolean bypass) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        when(livingEntity.getArmorValue()).thenReturn(12);
        when(livingEntity.getAttributeValue(Attributes.ARMOR_TOUGHNESS)).thenReturn(4.0D);
        final DamageSource damageSource = mock(DamageSource.class);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(ServerLevel.class)), null, source(damageSource), DamageCause.CUSTOM, 10.0D
        );

        if (bypass) {
            tag("bypasses_armor", DamageType.GENERIC);
        }

        try (final MockedStatic<CombatRules> combatRulesMockedStatic = mockStatic(CombatRules.class)) {
            combatRulesMockedStatic.when(() -> CombatRules.getDamageAfterAbsorb(livingEntity, 10.0F, damageSource, 12.0F, 4.0F)).thenReturn(6.0F);
            new ArmourReductionListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.ARMOUR));
            combatRulesMockedStatic.verify(
                    () -> CombatRules.getDamageAfterAbsorb(livingEntity, 10.0F, damageSource, 12.0F, 4.0F), times(bypass ? 0 : 1)
            );
            assertEquals(bypass ? 10.0D : 6.0D, customDamageEvent.getDamage());
        }
    }

    @Test
    void genericSourceWithoutAttackerIsSafe() {
        final CustomDamageEvent customDamageEvent = damage(
                craft(mock(LivingEntity.class), mock(ServerLevel.class)), null,
                org.bukkit.damage.DamageSource.builder(DamageType.GENERIC).build(), DamageCause.CUSTOM, 4.0D
        );

        try (final MockedStatic<CombatRules> combatRulesMockedStatic = mockStatic(CombatRules.class)) {
            assertDoesNotThrow(() -> new ArmourReductionListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.ARMOUR)));
        }
    }
}
