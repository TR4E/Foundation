package me.trae.foundation.minecraft.paper.addon.resourcepack.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.minecraft.paper.addon.resourcepack.ResourcePack;
import me.trae.foundation.minecraft.paper.addon.resourcepack.data.PlayerResourcePackData;
import me.trae.foundation.minecraft.paper.addon.resourcepack.events.ResourcePackSendEvent;
import me.trae.foundation.minecraft.paper.addon.resourcepack.settings.ResourcePackAddonSettings;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Getter
@AddonSingleton
public final class ResourcePackService implements Lifecycle {

    private final ConcurrentHashMap<UUID, PlayerResourcePackData> playerResourcePackDataMap = new ConcurrentHashMap<>();

    private final List<ResourcePack> resourcePackList;
    private final ResourcePackAddonSettings settings;

    private Map<UUID, ResourcePack> resourcePackMap = Collections.emptyMap();

    @Override
    public void onLiveDependencyUpdate() {
        final Map<UUID, ResourcePack> resourcePackMap = new HashMap<>();

        for (final ResourcePack resourcePack : this.resourcePackList) {
            resourcePackMap.put(resourcePack.getUniqueId(), resourcePack);
        }

        this.resourcePackMap = Map.copyOf(resourcePackMap);
    }

    public Optional<ResourcePack> getResourcePackByUniqueId(final UUID uniqueId) {
        return Optional.ofNullable(this.resourcePackMap.get(uniqueId));
    }

    public Optional<PlayerResourcePackData> getPlayerResourcePackDataByPlayer(final Player player) {
        return Optional.ofNullable(this.playerResourcePackDataMap.get(player.getUniqueId()));
    }

    public void sync(final Player player) {
        final PlayerResourcePackData playerResourcePackData = this.playerResourcePackDataMap.computeIfAbsent(player.getUniqueId(), _ -> new PlayerResourcePackData());

        final List<ResourcePack> requiredList = new ArrayList<>();
        final List<ResourcePack> optionalList = new ArrayList<>();

        final List<UUID> uniqueIdList = new ArrayList<>();

        for (final ResourcePack resourcePack : this.resourcePackList) {
            final boolean exists = playerResourcePackData.isLoaded(resourcePack) || playerResourcePackData.isPending(resourcePack);

            if (resourcePack.canLoad() && resourcePack.canLoad(player)) {
                if (exists) {
                    continue;
                }

                if (resourcePack.isRequired()) {
                    requiredList.add(resourcePack);
                } else {
                    optionalList.add(resourcePack);
                }
            } else if (exists) {
                playerResourcePackData.removeLoaded(resourcePack);
                playerResourcePackData.removePending(resourcePack);

                uniqueIdList.add(resourcePack.getUniqueId());
            }
        }

        if (!uniqueIdList.isEmpty()) {
            player.removeResourcePacks(uniqueIdList);
        }

        if (!requiredList.isEmpty() || !optionalList.isEmpty()) {
            this.settings.onApplying(player);
        }

        this.send(player, playerResourcePackData, requiredList, true);
        this.send(player, playerResourcePackData, optionalList, false);
    }

    public void cleanup(final Player player) {
        this.playerResourcePackDataMap.remove(player.getUniqueId());
    }

    private void send(final Player player, final PlayerResourcePackData playerResourcePackData, final List<ResourcePack> resourcePacks, final boolean required) {
        if (resourcePacks.isEmpty()) {
            return;
        }

        for (final ResourcePack resourcePack : resourcePacks) {
            playerResourcePackData.addPending(resourcePack);

            UtilEvent.dispatch(new ResourcePackSendEvent(resourcePack, player));

            resourcePack.onLoading(player);
        }

        player.sendResourcePacks(
                ResourcePackRequest.resourcePackRequest()
                        .packs(resourcePacks)
                        .replace(false)
                        .required(required)
                        .build()
        );
    }
}