package me.trae.foundation.minecraft.paper.addon.damage.listeners;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.damage.services.DamageService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@AllArgsConstructor
@AddonSingleton
public final class DamageCleanupListener implements Listener {

    private final DamageService damageService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoveFromWorld(final EntityRemoveFromWorldEvent event) {
        this.damageService.getLastDamageMap().remove(event.getEntity().getUniqueId());
    }
}