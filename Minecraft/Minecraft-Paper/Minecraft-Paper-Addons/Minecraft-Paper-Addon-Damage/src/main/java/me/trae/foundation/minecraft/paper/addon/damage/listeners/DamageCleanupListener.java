package me.trae.foundation.minecraft.paper.addon.damage.listeners;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.damage.services.DamageService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

@AllArgsConstructor
@AddonSingleton
public final class DamageCleanupListener implements Listener {

    private final DamageService damageService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoveFromWorld(final EntityRemoveFromWorldEvent event) {
        final UUID uniqueId = event.getEntity().getUniqueId();

        this.damageService.getLastDamageMap().remove(uniqueId);

        this.damageService.getLastPlayerDamagerDamageMap().remove(uniqueId);
    }
}