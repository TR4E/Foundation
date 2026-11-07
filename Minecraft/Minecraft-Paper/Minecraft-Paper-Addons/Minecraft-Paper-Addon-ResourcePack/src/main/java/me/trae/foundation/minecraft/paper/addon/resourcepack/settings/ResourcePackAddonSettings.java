package me.trae.foundation.minecraft.paper.addon.resourcepack.settings;

import me.trae.foundation.minecraft.common.adventure.UtilAdventure;
import me.trae.foundation.minecraft.paper.addon.resourcepack.ResourcePack;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.settings.AddonSettings;
import me.trae.foundation.minecraft.paper.plugin.framework.stash.TitleStash;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public interface ResourcePackAddonSettings extends AddonSettings {

    default void onApplying(final Player player) {
        TitleStash.of(
                Component.empty(),
                Component.text("Applying resource packs", NamedTextColor.GOLD),
                250L,
                2000L,
                250L
        ).send(player);
    }

    default void onApplied(final Player player) {
        TitleStash.of(
                Component.empty(),
                Component.text("Applied resource packs", NamedTextColor.GREEN),
                250L,
                2000L,
                250L
        ).send(player);
    }

    default Component getKickMessage(final ResourcePack resourcePack) {
        return UtilAdventure.join(
                Component.text("You must accept the server resource pack to play.", NamedTextColor.RED),
                Component.newline(),
                Component.newline(),
                Component.text("Set \"Server Resource Packs\" to Enabled in your server list entry, then reconnect.", NamedTextColor.GRAY)
        );
    }
}