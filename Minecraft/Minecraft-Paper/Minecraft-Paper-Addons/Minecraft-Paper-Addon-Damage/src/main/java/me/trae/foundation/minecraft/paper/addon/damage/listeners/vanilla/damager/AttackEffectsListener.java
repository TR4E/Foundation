package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damager.AttackEffectsEvent;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackSnapshotListener.AttackSnapshot;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

@AllArgsConstructor
@AddonSingleton
public final class AttackEffectsListener implements Listener {

    private final AttackSnapshotListener attackSnapshotListener;

    @EventHandler
    public void onDamageApplied(final DamageAppliedEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || damageEvent.getBlockedDamage() > 0.0D || damageEvent.getCause() != DamageCause.ENTITY_ATTACK || damageEvent.getProjectile() != null || !Player.class.isInstance(damageEvent.getDamager())) {
            return;
        }

        final Player player = Player.class.cast(damageEvent.getDamager());

        final AttackSnapshot attackSnapshot = this.attackSnapshotListener.getSnapshot(damageEvent);

        final Sound defaultSound = damageEvent.isCritical() ? Sound.ENTITY_PLAYER_ATTACK_CRIT : attackSnapshot != null && attackSnapshot.strength() > 0.9F ? Sound.ENTITY_PLAYER_ATTACK_STRONG : Sound.ENTITY_PLAYER_ATTACK_WEAK;

        final AttackEffectsEvent effectsEvent = UtilEvent.supply(new AttackEffectsEvent(damageEvent, defaultSound, 0.1F, damageEvent.isCritical() ? 12 : 0));

        if (effectsEvent.isCancelled()) {
            return;
        }

        if (effectsEvent.getSound() != null) {
            player.getWorld().playSound(player.getLocation(), effectsEvent.getSound(), 1.0F, 1.0F);
        }

        if (effectsEvent.getCriticalParticles() > 0) {
            player.getWorld().spawnParticle(Particle.CRIT, damageEvent.getDamagee().getLocation().add(0.0D, 1.0D, 0.0D), effectsEvent.getCriticalParticles(), 0.2D, 0.3D, 0.2D, 0.0D);
        }

        if (effectsEvent.getExhaustion() > 0.0F) {
            player.setExhaustion(player.getExhaustion() + effectsEvent.getExhaustion());
        }

        final int damage = (int) Math.round(damageEvent.getFinalDamage() * 10.0D);

        if (damage > 0) {
            player.incrementStatistic(Statistic.DAMAGE_DEALT, damage);
        }
    }
}