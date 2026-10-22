package me.trae.foundation.minecraft.paper.addon.chat.events;

import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.chat.channels.ChatChannel;
import me.trae.foundation.minecraft.paper.plugin.framework.event.asynchronous.CustomAsynchronousCancellableEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public final class ChatSendEvent extends CustomAsynchronousCancellableEvent {

    private final Player sender;
    private final ChatChannel channel;
    private final Component message;

    public ChatSendEvent(final AsyncChatEvent asyncChatEvent, final ChatChannel channel) {
        this(
                asyncChatEvent.getPlayer(),
                channel,
                asyncChatEvent.message()
        );
    }
}