package me.trae.foundation.minecraft.paper.addon.chat.placeholders;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public non-sealed interface StaticChatPlaceholder extends ChatPlaceholder {

    Component apply(final Player sender, final Component message);
}