package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

@org.junit.jupiter.api.Tag("VERIFIED")
class AttackEnchantmentListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({"ENTITY_ATTACK,false,0,true", "PROJECTILE,true,0,true", "ENTITY_ATTACK,false,2,false", "ENTITY_SWEEP_ATTACK,false,0,true"})
    void postAttackEffectsIncludeSweptEntitiesButSkipBlockedHits(
            final DamageCause damageCause,
            final boolean projectile,
            final double blocked,
            final boolean expected
    ) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final DamageSource damageSource = mock(DamageSource.class);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, serverLevel),
                entity(),
                projectile ? mock(Projectile.class) : null,
                source(damageSource),
                damageCause,
                4.0D,
                null,
                null
        );
        customDamageEvent.setBlockedDamage(blocked);

        try (final MockedStatic<EnchantmentHelper> enchantmentHelperMockedStatic = mockStatic(EnchantmentHelper.class)) {
            new AttackEnchantmentListener().onDamageApplied(new DamageAppliedEvent(customDamageEvent));
            enchantmentHelperMockedStatic.verify(() -> EnchantmentHelper.doPostAttackEffects(serverLevel, livingEntity, damageSource), times(expected ? 1 : 0));
        }
    }
}
