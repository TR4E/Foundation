package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@AddonSingleton
public final class MobEffectHurtListener implements Listener {

    @EventHandler
    public void onDamageApplied(final DamageAppliedEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || damageEvent.getBlockedDamage() > 0.0D || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee()) || !CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        final ServerLevel serverLevel = ServerLevel.class.cast(entity.level());

        final DamageSource damageSource = CraftDamageSource.class.cast(damageEvent.getSource()).getHandle();

        final float damage = (float) damageEvent.getFinalDamage();

        for (final MobEffectInstance mobEffectInstance : entity.getActiveEffects()) {
            mobEffectInstance.onMobHurt(serverLevel, entity, damageSource, damage);
        }
    }
}