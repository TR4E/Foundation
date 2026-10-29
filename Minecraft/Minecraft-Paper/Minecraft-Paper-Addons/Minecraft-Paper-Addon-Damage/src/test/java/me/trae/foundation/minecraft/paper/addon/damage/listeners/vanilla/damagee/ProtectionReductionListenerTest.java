package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class ProtectionReductionListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({"none,10,true", "bypasses_effects,10,false", "bypasses_enchantments,10,false", "none,0,false", "none,-1,false"})
    void appliesProtectionAndSkipsBypassOrNonpositiveDamage(final String bypass, final double amount, final boolean expected) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final DamageSource damageSource = mock(DamageSource.class);
        final org.bukkit.craftbukkit.damage.CraftDamageSource craftDamageSource = source(damageSource);
        when(craftDamageSource.getDamageType()).thenReturn(DamageType.GENERIC);
        tag(bypass, DamageType.GENERIC);
        final CustomDamageEvent customDamageEvent = damage(craft(livingEntity, serverLevel), null, craftDamageSource, DamageCause.CUSTOM, amount);

        try (final MockedStatic<EnchantmentHelper> enchantmentHelperMockedStatic = mockStatic(EnchantmentHelper.class);
             final MockedStatic<CombatRules> combatRulesMockedStatic = mockStatic(CombatRules.class)) {
            enchantmentHelperMockedStatic.when(() -> EnchantmentHelper.getDamageProtection(serverLevel, livingEntity, damageSource)).thenReturn(5.0F);
            combatRulesMockedStatic.when(() -> CombatRules.getDamageAfterMagicAbsorb(10.0F, 5.0F)).thenReturn(8.0F);
            assertDoesNotThrow(() -> new ProtectionReductionListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.PROTECTION)));
            enchantmentHelperMockedStatic.verify(() -> EnchantmentHelper.getDamageProtection(serverLevel, livingEntity, damageSource), times(expected ? 1 : 0));
            combatRulesMockedStatic.verify(() -> CombatRules.getDamageAfterMagicAbsorb(10.0F, 5.0F), times(expected ? 1 : 0));
            assertEquals(expected ? 8.0D : amount, customDamageEvent.getDamage());
        }
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
        try (final MockedStatic<EnchantmentHelper> enchantmentHelperMockedStatic = mockStatic(EnchantmentHelper.class);
             final MockedStatic<CombatRules> combatRulesMockedStatic = mockStatic(CombatRules.class)) {
            assertDoesNotThrow(() -> new ProtectionReductionListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.PROTECTION)));
        }
    }
}
