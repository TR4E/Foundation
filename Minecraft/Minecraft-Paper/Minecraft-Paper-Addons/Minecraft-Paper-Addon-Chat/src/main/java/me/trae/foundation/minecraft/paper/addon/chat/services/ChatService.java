package me.trae.foundation.minecraft.paper.addon.chat.services;

import lombok.RequiredArgsConstructor;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.minecraft.paper.addon.chat.channels.ChatChannel;
import me.trae.foundation.minecraft.paper.addon.chat.channels.DefaultChatChannel;
import me.trae.foundation.minecraft.paper.addon.chat.events.ChatSelectChannelEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@AddonSingleton
public final class ChatService implements Lifecycle {

    private final List<ChatChannel> channelList;
    private final List<DefaultChatChannel> defaultChatChannelList;

    private volatile Map<String, ChatChannel> channelByIdentifierMap = Map.of();

    private volatile DefaultChatChannel defaultChatChannel;

    @Override
    public void onLiveDependencyUpdate() {
        final Map<String, ChatChannel> channelByIdentifierMap = new HashMap<>();

        for (final ChatChannel chatChannel : this.channelList) {
            channelByIdentifierMap.put(chatChannel.getIdentifier().toLowerCase(Locale.ROOT), chatChannel);
        }

        this.channelByIdentifierMap = Map.copyOf(channelByIdentifierMap);
        this.defaultChatChannel = this.defaultChatChannelList.stream().min(Comparator.comparingInt(DefaultChatChannel::getPriority)).orElse(null);
    }

    public Optional<ChatChannel> getChannelByIdentifier(final String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(this.channelByIdentifierMap.get(identifier.toLowerCase(Locale.ROOT)));
    }

    public Optional<ChatChannel> getSelectedChannel(final Player player) {
        return Optional.ofNullable(UtilEvent.supply(new ChatSelectChannelEvent(player, this.defaultChatChannel)).getChannel());
    }
}