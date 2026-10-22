package me.trae.foundation.minecraft.paper.addon.chat.channels;

import me.trae.foundation.minecraft.paper.addon.chat.placeholders.ChatPlaceholder;
import org.bukkit.entity.Player;

import java.util.List;

public interface ChatChannel {

    String getIdentifier();

    List<Player> getRecipients(final Player sender);

    List<ChatPlaceholder> getPlaceholders();
}