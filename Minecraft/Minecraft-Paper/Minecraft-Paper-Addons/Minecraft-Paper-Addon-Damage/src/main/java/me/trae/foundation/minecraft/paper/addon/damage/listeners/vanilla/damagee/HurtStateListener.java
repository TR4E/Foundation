package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@AddonSingleton
public final class HurtStateListener implements Listener {

    private static final int HURT_DURATION = 10;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomDamage(final CustomDamageEvent event) {
        if (!CraftLivingEntity.class.isInstance(event.getDamagee())) {
            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(event.getDamagee()).getHandle();

        if (entity.isSleeping()) {
            entity.stopSleeping();
        }

        entity.setNoActionTime(0);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageApplied(final DamageAppliedEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (!damageEvent.isFullDamage() || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee())) {
            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        entity.hurtDuration = HURT_DURATION;

        entity.hurtTime = entity.hurtDuration;
    }
}