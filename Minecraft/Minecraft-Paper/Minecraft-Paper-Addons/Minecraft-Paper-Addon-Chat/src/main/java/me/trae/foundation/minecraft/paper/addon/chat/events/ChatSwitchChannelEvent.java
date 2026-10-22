package me.trae.foundation.minecraft.paper.addon.chat.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.chat.channels.ChatChannel;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public final class ChatSwitchChannelEvent extends CustomEvent {

    private final Player player;
    private final ChatChannel previousChannel, channel;
}