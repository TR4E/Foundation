package me.trae.foundation.minecraft.paper.addon.item.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.injector.extensions.scheduler.annotation.Scheduler;
import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.addon.item.enums.ActivateType;
import me.trae.foundation.minecraft.paper.addon.item.events.ItemChannelEvent;
import me.trae.foundation.minecraft.paper.addon.item.events.ItemPostActivateEvent;
import me.trae.foundation.minecraft.paper.addon.item.events.ItemPreActivateEvent;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.addon.item.types.ActivatableCustomItem;
import me.trae.foundation.minecraft.paper.addon.item.types.ChannelCustomItem;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilMaterial;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@AddonSingleton
public final class ItemActivateListener implements Listener {

    private final ItemService itemService;

    private final Map<UUID, Integer> blockedClickTickMap = new HashMap<>();
    private final Map<UUID, Integer> clickTickMap = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        final Player player = event.getPlayer();
        final Action action = event.getAction();
        final int tick = Bukkit.getCurrentTick();

        if (action.isRightClick()) {
            this.blockedClickTickMap.put(player.getUniqueId(), tick);
        } else if (this.isRecent(this.blockedClickTickMap, player.getUniqueId(), tick)) {
            return;
        }

        this.clickTickMap.put(player.getUniqueId(), tick);

        final ItemStack itemStack = event.getItem();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        ActivateType.getByAction(action).ifPresent(activateType -> {
            this.itemService.getItemByItemStack(itemStack).ifPresent(item -> {
                if (!(item instanceof final ActivatableCustomItem activatableCustomItem)) {
                    return;
                }

                if (!activatableCustomItem.getSupportedActivateTypes().contains(activateType)) {
                    return;
                }

                final Block clickedBlock = event.getClickedBlock();

                // Consumable materials defer to vanilla unless the item opts in, so a custom golden apple is eaten rather than activated. Returning skips the activation entirely.
                // Example: return on a custom Ender Pearl because a right click would throw it.
                if (activateType == ActivateType.RIGHT_CLICK && !activatableCustomItem.activateOnItemUse(player, itemStack, activateType) && UtilMaterial.isUsable(itemStack.getType())) {
                    return;
                }

                // A block that responds on its own wins unless the item opts in, so a chest opens instead of activating. Sneaking is exempt, since vanilla skips the block entirely with a full hand. Returning skips the activation entirely.
                // Example: return on a Chest because a right click would open it, or on Grass because a right click with a Hoe would till it.
                if (activateType == ActivateType.RIGHT_CLICK && clickedBlock != null && !player.isSneaking() && !activatableCustomItem.activateOnBlockUse(player, itemStack, clickedBlock, activateType) && (UtilMaterial.isInteractable(clickedBlock.getType()) || UtilMaterial.isInteractableByHand(clickedBlock.getType(), itemStack))) {
                    return;
                }

                // Denies the material's own use by default, so an ender pearl activates without leaving the hand.
                event.setUseItemInHand(activatableCustomItem.useItemInHand(player, itemStack, activateType));

                // Leaves the block's response untouched by default, letting the item suppress it per block.
                if (clickedBlock != null) {
                    event.setUseInteractedBlock(activatableCustomItem.useInteractedBlock(player, itemStack, clickedBlock, activateType));
                }

                this.activate(player, itemStack, clickedBlock, activatableCustomItem, activateType);
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(final PlayerDropItemEvent event) {
        final ItemStack itemStack = event.getItemDrop().getItemStack();

        final CustomItem customItem = this.itemService.getItemByItemStack(itemStack).orElse(null);
        if (customItem == null) {
            return;
        }

        final Player player = event.getPlayer();

        final int tick = Bukkit.getCurrentTick();

        this.blockedClickTickMap.put(player.getUniqueId(), tick);

        if (this.isRecent(this.clickTickMap, player.getUniqueId(), tick)) {
            return;
        }

        if (!(customItem instanceof final ActivatableCustomItem activatableCustomItem)) {
            return;
        }

        if (!activatableCustomItem.getSupportedActivateTypes().contains(ActivateType.DROP_ITEM)) {
            return;
        }

        event.setCancelled(true);

        this.activate(player, itemStack, null, activatableCustomItem, ActivateType.DROP_ITEM);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(final PlayerSwapHandItemsEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final ItemStack itemStack = event.getOffHandItem();

        final CustomItem customItem = this.itemService.getItemByItemStack(itemStack).orElse(null);
        if (customItem == null) {
            return;
        }

        if (!(customItem instanceof final ActivatableCustomItem activatableCustomItem)) {
            return;
        }

        if (!(activatableCustomItem.getSupportedActivateTypes().contains(ActivateType.SWAP_HAND))) {
            return;
        }

        event.setCancelled(true);

        this.activate(event.getPlayer(), itemStack, null, activatableCustomItem, ActivateType.SWAP_HAND);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.blockedClickTickMap.remove(event.getPlayer().getUniqueId());
        this.clickTickMap.remove(event.getPlayer().getUniqueId());
    }

    @Scheduler(period = 50, unit = TimeUnit.MILLISECONDS)
    public void onScheduler() {
        for (final CustomItem item : this.itemService.getItems()) {
            if (!(item instanceof final ChannelCustomItem channelCustomItem)) {
                continue;
            }

            channelCustomItem.getActiveChannelSet().removeIf(uuid -> {
                final Player player = UtilServer.getOnlinePlayerById(uuid).orElse(null);
                if (player == null) {
                    return true;
                }

                final ItemStack itemStack = player.getInventory().getItemInMainHand();

                if (!channelCustomItem.isSimilarByIdentifier(itemStack) || !player.isHandRaised()) {
                    channelCustomItem.onStop(player, itemStack);
                    return true;
                }

                if (UtilEvent.supply(new ItemChannelEvent(channelCustomItem, player, itemStack)).isCancelled() || !channelCustomItem.canActivate(player, itemStack, null) || !channelCustomItem.canChannel(player, itemStack)) {
                    channelCustomItem.onStop(player, itemStack);
                    return true;
                }

                channelCustomItem.onChannel(player, itemStack);
                return false;
            });
        }
    }

    private boolean isRecent(final Map<UUID, Integer> tickMap, final UUID uuid, final int tick) {
        final Integer recordedTick = tickMap.get(uuid);

        return recordedTick != null && tick - recordedTick <= 1;
    }

    private void activate(final Player player, final ItemStack itemStack, final Block clickedBlock, final ActivatableCustomItem activatableCustomItem, final ActivateType activateType) {
        if (UtilEvent.supply(new ItemPreActivateEvent(activatableCustomItem, player, itemStack, clickedBlock, activateType)).isCancelled()) {
            return;
        }

        if (!activatableCustomItem.canActivate(player, itemStack, clickedBlock, activateType)) {
            return;
        }

        activatableCustomItem.onActivate(player, itemStack, clickedBlock, activateType);

        UtilEvent.dispatch(new ItemPostActivateEvent(activatableCustomItem, player, itemStack, clickedBlock, activateType));
    }
}