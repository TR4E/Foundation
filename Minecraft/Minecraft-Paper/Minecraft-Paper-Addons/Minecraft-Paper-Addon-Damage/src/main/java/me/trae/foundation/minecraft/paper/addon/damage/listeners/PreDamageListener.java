package me.trae.foundation.minecraft.paper.addon.damage.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.services.DamageService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Damageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

@AllArgsConstructor
@AddonSingleton
public final class PreDamageListener implements Listener {

    private final DamageService damageService;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(final EntityDamageEvent entityDamageEvent) {
        if (entityDamageEvent.isCancelled()) {
            return;
        }

        if (!(entityDamageEvent.getEntity() instanceof Damageable) || entityDamageEvent.getEntity() instanceof ComplexEntityPart) {
            return;
        }

        entityDamageEvent.setCancelled(true);

        if (entityDamageEvent instanceof final EntityDamageByEntityEvent entityDamageByEntityEvent) {
            this.damageService.process(new CustomDamageEvent(entityDamageByEntityEvent));
        } else {
            this.damageService.process(new CustomDamageEvent(entityDamageEvent));
        }
    }
}