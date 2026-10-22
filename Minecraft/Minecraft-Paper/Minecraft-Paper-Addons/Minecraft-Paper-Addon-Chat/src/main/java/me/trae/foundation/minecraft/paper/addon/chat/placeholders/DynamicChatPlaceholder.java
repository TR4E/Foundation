package me.trae.foundation.minecraft.paper.addon.chat.placeholders;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public non-sealed interface DynamicChatPlaceholder extends ChatPlaceholder {

    Component apply(final Player sender, final Player recipient, final Component message);
}