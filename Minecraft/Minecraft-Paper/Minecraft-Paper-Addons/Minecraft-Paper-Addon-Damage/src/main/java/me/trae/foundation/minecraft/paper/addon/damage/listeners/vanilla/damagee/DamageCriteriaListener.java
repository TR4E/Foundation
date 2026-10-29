package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@AddonSingleton
public final class DamageCriteriaListener implements Listener {

    private static final float STATISTIC_SCALE = 10.0F;

    @EventHandler
    public void onDamageApplied(final DamageAppliedEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee()) || !CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        final DamageSource damageSource = CraftDamageSource.class.cast(damageEvent.getSource()).getHandle();

        final float originalDamage = (float) damageEvent.getOriginalDamage();

        final float damage = (float) damageEvent.getFinalDamage();

        final float blockedDamage = (float) damageEvent.getBlockedDamage();

        final boolean blocked = blockedDamage > 0.0F;

        if (ServerPlayer.class.isInstance(entity)) {
            final ServerPlayer serverPlayer = ServerPlayer.class.cast(entity);

            CriteriaTriggers.ENTITY_HURT_PLAYER.trigger(serverPlayer, damageSource, originalDamage, damage, blocked);

            if (blocked) {
                serverPlayer.awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(blockedDamage * STATISTIC_SCALE));
            }
        }

        if (!CraftLivingEntity.class.isInstance(damageEvent.getDamager())) {
            return;
        }

        final LivingEntity damager = CraftLivingEntity.class.cast(damageEvent.getDamager()).getHandle();

        if (!ServerPlayer.class.isInstance(damager)) {
            return;
        }

        CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(ServerPlayer.class.cast(damager), entity, damageSource, originalDamage, damage, blocked);
    }
}