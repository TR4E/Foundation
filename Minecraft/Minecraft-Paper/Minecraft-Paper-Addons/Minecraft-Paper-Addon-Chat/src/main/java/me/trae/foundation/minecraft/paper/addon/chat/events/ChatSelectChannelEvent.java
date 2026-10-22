package me.trae.foundation.minecraft.paper.addon.chat.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.chat.channels.ChatChannel;
import me.trae.foundation.minecraft.paper.plugin.framework.event.asynchronous.CustomAsynchronousEvent;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
@Setter
public final class ChatSelectChannelEvent extends CustomAsynchronousEvent {

    private final Player sender;

    private ChatChannel channel;
}