package me.trae.foundation.minecraft.paper.plugin.framework.addon.listener;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.AddonRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceUnregisterEvent;

@AllArgsConstructor
public final class AddonListener implements Listener {

    private final AddonRegistry addonRegistry;

    @EventHandler
    public void onServiceUnregister(final ServiceUnregisterEvent event) {
        if (!event.getProvider().getService().equals(String.class)) {
            return;
        }

        this.addonRegistry.elect();
    }
}