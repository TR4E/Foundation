package me.trae.foundation.minecraft.paper.addon.chat.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.AllArgsConstructor;
import lombok.CustomLog;
import me.trae.foundation.minecraft.common.adventure.UtilAdventure;
import me.trae.foundation.minecraft.paper.addon.chat.channels.ChatChannel;
import me.trae.foundation.minecraft.paper.addon.chat.events.ChatReceiveEvent;
import me.trae.foundation.minecraft.paper.addon.chat.events.ChatSendEvent;
import me.trae.foundation.minecraft.paper.addon.chat.placeholders.ChatPlaceholder;
import me.trae.foundation.minecraft.paper.addon.chat.placeholders.DynamicChatPlaceholder;
import me.trae.foundation.minecraft.paper.addon.chat.placeholders.StaticChatPlaceholder;
import me.trae.foundation.minecraft.paper.addon.chat.services.ChatService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

@CustomLog
@AllArgsConstructor
@AddonSingleton
public final class ChatListener implements Listener {

    private final ChatService chatService;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChat(final AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }

        event.setCancelled(true);

        final Player player = event.getPlayer();

        final ChatChannel selectedChatChannel = this.chatService.getSelectedChannel(player).orElse(null);
        if (selectedChatChannel == null) {
            LOGGER.info("Skipped chat message from '{}' because no chat channel was selected.", player.getName());
            return;
        }

        UtilEvent.dispatch(new ChatSendEvent(event, selectedChatChannel));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChatSend(final ChatSendEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Player sender = event.getSender();
        final ChatChannel chatChannel = event.getChannel();
        final Component message = event.getMessage();

        final List<ChatPlaceholder> placeholders = chatChannel.getPlaceholders();

        final Component[] components = new Component[placeholders.size()];

        for (int index = 0; index < components.length; index++) {
            if (!(placeholders.get(index) instanceof final StaticChatPlaceholder staticChatPlaceholder)) {
                continue;
            }

            components[index] = staticChatPlaceholder.apply(sender, message);
        }

        for (final Player recipient : chatChannel.getRecipients(sender)) {
            UtilEvent.dispatch(new ChatReceiveEvent(event, recipient, components.clone()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChatReceive(final ChatReceiveEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Player sender = event.getSender();
        final Player recipient = event.getRecipient();
        final ChatChannel chatChannel = event.getChannel();
        final Component message = event.getMessage();

        final List<ChatPlaceholder> placeholders = chatChannel.getPlaceholders();

        final Component[] components = event.getComponents();

        for (int index = 0; index < components.length; index++) {
            if (!(placeholders.get(index) instanceof final DynamicChatPlaceholder dynamicChatPlaceholder)) {
                continue;
            }

            components[index] = dynamicChatPlaceholder.apply(sender, recipient, message);
        }

        UtilMessage.message(recipient, UtilAdventure.joinWithSpace(components));
    }
}