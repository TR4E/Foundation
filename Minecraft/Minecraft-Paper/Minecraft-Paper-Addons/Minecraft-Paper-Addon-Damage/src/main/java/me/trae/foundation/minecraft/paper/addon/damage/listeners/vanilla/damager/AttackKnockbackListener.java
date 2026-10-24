package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageKnockbackEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damager.AttackKnockbackEvent;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackSnapshotListener.AttackSnapshot;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@AddonSingleton
public final class AttackKnockbackListener implements Listener {

    private final AttackSnapshotListener attackSnapshotListener;

    @EventHandler
    public void onDamageKnockback(final DamageKnockbackEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (event.isCancelled() || damageEvent.getCause() != DamageCause.ENTITY_ATTACK || damageEvent.getProjectile() != null || !Player.class.isInstance(damageEvent.getDamager())) {
            return;
        }

        final AttackSnapshot attackSnapshot = this.attackSnapshotListener.getSnapshot(damageEvent);
        if (attackSnapshot == null) {
            return;
        }

        final ItemStack itemStack = damageEvent.getItemStack();

        final int knockbackLevel = itemStack == null ? 0 : itemStack.getEnchantmentLevel(Enchantment.KNOCKBACK);

        final double bonusStrength = 0.5D * (knockbackLevel + (attackSnapshot.sprinting() && attackSnapshot.strength() > 0.9F ? 1 : 0));

        if (bonusStrength <= 0.0D) {
            return;
        }

        final AttackKnockbackEvent knockbackEvent = UtilEvent.supply(new AttackKnockbackEvent(damageEvent, bonusStrength, attackSnapshot.sprinting()));

        if (knockbackEvent.isCancelled()) {
            return;
        }

        event.setStrength(Math.max(0.0D, event.getStrength() + knockbackEvent.getBonusStrength()));

        event.setSprintReset(knockbackEvent.isSprintReset());
    }
}