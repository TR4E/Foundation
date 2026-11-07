package me.trae.foundation.minecraft.paper.addon.resourcepack.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.resourcepack.ResourcePack;
import me.trae.foundation.minecraft.paper.addon.resourcepack.data.PlayerResourcePackData;
import me.trae.foundation.minecraft.paper.addon.resourcepack.events.ResourcePackLoadedEvent;
import me.trae.foundation.minecraft.paper.addon.resourcepack.services.ResourcePackService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

@AllArgsConstructor
@AddonSingleton
public final class ResourcePackListener implements Listener {

    private final ResourcePackService resourcePackService;

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        this.resourcePackService.sync(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.resourcePackService.cleanup(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {
        this.resourcePackService.sync(event.getPlayer());
    }

    @EventHandler
    public void onPlayerResourcePackStatus(final PlayerResourcePackStatusEvent event) {
        final Player player = event.getPlayer();

        final PlayerResourcePackData playerResourcePackData = this.resourcePackService.getPlayerResourcePackDataByPlayer(player).orElse(null);
        if (playerResourcePackData == null) {
            return;
        }

        final ResourcePack resourcePack = this.resourcePackService.getResourcePackByUniqueId(event.getID()).orElse(null);
        if (resourcePack == null) {
            return;
        }

        if (!playerResourcePackData.isPending(resourcePack)) {
            return;
        }

        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                playerResourcePackData.removePending(resourcePack);
                playerResourcePackData.addLoaded(resourcePack);

                UtilEvent.dispatch(new ResourcePackLoadedEvent(resourcePack, player));

                resourcePack.onLoaded(player);

                if (!playerResourcePackData.isAnyPending()) {
                    this.resourcePackService.getSettings().onApplied(player);
                }
                break;
            }
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                playerResourcePackData.removePending(resourcePack);

                if (resourcePack.isRequired()) {
                    this.resourcePackService.cleanup(player);

                    player.kick(this.resourcePackService.getSettings().getKickMessage(resourcePack));
                    break;
                }
            }
            case DISCARDED -> {
                playerResourcePackData.removePending(resourcePack);
                break;
            }
        }
    }
}