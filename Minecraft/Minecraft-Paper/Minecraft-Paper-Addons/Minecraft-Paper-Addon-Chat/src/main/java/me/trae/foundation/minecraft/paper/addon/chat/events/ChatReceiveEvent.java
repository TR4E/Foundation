package me.trae.foundation.minecraft.paper.addon.chat.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.chat.channels.ChatChannel;
import me.trae.foundation.minecraft.paper.plugin.framework.event.asynchronous.CustomAsynchronousCancellableEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public final class ChatReceiveEvent extends CustomAsynchronousCancellableEvent {

    private final Player sender, recipient;
    private final ChatChannel channel;
    private final Component message;
    private final Component[] components;

    public ChatReceiveEvent(final ChatSendEvent chatSendEvent, final Player recipient, final Component[] components) {
        this(
                chatSendEvent.getSender(),
                recipient,
                chatSendEvent.getChannel(),
                chatSendEvent.getMessage(),
                components
        );
    }
}