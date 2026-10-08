package me.trae.foundation.minecraft.paper.addon.item.types;

import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.item.enums.ActivateType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public abstract class ChannelCustomItem extends SingleActivatableCustomItem {

    @Getter
    private final Set<UUID> activeChannelSet = new HashSet<>();

    public ChannelCustomItem(final Material material, final String identifier, final String namespace) {
        super(material, identifier, namespace, ActivateType.RIGHT_CLICK);
    }

    @Override
    public final void onActivate(final Player player, final ItemStack itemStack, final Block clickedBlock) {
        if (this.activeChannelSet.add(player.getUniqueId())) {
            this.onStart(player, itemStack);
        }
    }

    public boolean canChannel(final Player player, final ItemStack itemStack) {
        return true;
    }

    public void onStart(final Player player, final ItemStack itemStack) {
    }

    public void onStop(final Player player, final ItemStack itemStack) {
    }

    public abstract void onChannel(final Player player, final ItemStack itemStack);

    public final List<Player> getChannelingPlayers() {
        return this.activeChannelSet.stream().map(Bukkit.getServer()::getPlayer).filter(Objects::nonNull).toList();
    }

    public final boolean isChanneling(final Player player) {
        return this.activeChannelSet.contains(player.getUniqueId());
    }
}