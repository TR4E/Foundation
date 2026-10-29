package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@AddonSingleton
public final class ReductionStatisticListener implements Listener {

    private static final float STATISTIC_SCALE = 10.0F;

    @EventHandler
    public void onDamageApplied(final DamageAppliedEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee())) {
            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        final ServerPlayer damagee = ServerPlayer.class.isInstance(entity) ? ServerPlayer.class.cast(entity) : null;

        final ServerPlayer damager = CraftPlayer.class.isInstance(damageEvent.getDamager()) ? CraftPlayer.class.cast(damageEvent.getDamager()).getHandle() : null;

        final float resistedDamage = (float) damageEvent.getResistedDamage();

        if (resistedDamage > 0.0F && resistedDamage < Float.MAX_VALUE) {
            if (damagee != null) {
                damagee.awardStat(Stats.DAMAGE_RESISTED, Math.round(resistedDamage * STATISTIC_SCALE));
            } else if (damager != null) {
                damager.awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(resistedDamage * STATISTIC_SCALE));
            }
        }

        final float absorbedDamage = (float) damageEvent.getAbsorbedDamage();

        if (absorbedDamage <= 0.0F || absorbedDamage >= Float.MAX_VALUE) {
            return;
        }

        if (damagee != null) {
            damagee.awardStat(Stats.DAMAGE_ABSORBED, Math.round(absorbedDamage * STATISTIC_SCALE));
        }

        if (damager != null) {
            damager.awardStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(absorbedDamage * STATISTIC_SCALE));
        }
    }
}