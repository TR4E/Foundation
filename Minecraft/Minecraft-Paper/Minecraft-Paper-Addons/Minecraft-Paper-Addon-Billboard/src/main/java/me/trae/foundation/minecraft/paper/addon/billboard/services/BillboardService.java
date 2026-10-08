package me.trae.foundation.minecraft.paper.addon.billboard.services;

import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.injector.extensions.scheduler.annotation.Scheduler;
import me.trae.foundation.minecraft.paper.addon.billboard.patch.BillboardPatch;
import me.trae.foundation.minecraft.paper.addon.billboard.types.Billboard;
import me.trae.foundation.minecraft.paper.addon.billboard.types.BillboardImage;
import me.trae.foundation.minecraft.paper.addon.billboard.types.BillboardVideo;
import me.trae.foundation.minecraft.paper.addon.billboard.utility.UtilMap;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilNms;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilTask;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@CustomLog
@RequiredArgsConstructor
@Getter
@AddonSingleton
public final class BillboardService implements Lifecycle, Listener {

    private static final int RECONCILE_INTERVAL = 10;

    private static final double DESPAWN_MARGIN = 8.0D;

    private static final long MAXIMUM_LAG = TimeUnit.SECONDS.toNanos(1L);

    private final Map<String, Billboard> billboardMap = new HashMap<>();

    private final List<Billboard> billboardList;

    private int passCount;

    @Override
    public void onLiveDependencyUpdate() {
        this.billboardMap.clear();

        this.billboardList.forEach(billboard -> this.billboardMap.put(billboard.getIdentifier(), billboard));
    }

    @EventHandler
    public void onServerLoad(final ServerLoadEvent event) {
        for (final Billboard billboard : this.billboardList) {
            if (!billboard.build()) {
                LOGGER.warn("Billboard {} is in a world that is not loaded", billboard.getIdentifier());
                continue;
            }

            UtilTask.executeAsynchronously(() -> {
                try {
                    billboard.load();
                    billboard.markLoaded();
                } catch (final IOException exception) {
                    LOGGER.warn("Failed to load billboard {}", billboard.getIdentifier(), exception);
                }
            });
        }
    }

    @Scheduler(period = 50, unit = TimeUnit.MILLISECONDS, asynchronous = true)
    public void onScheduler() {
        final long now = System.nanoTime();
        final boolean reconcile = this.passCount++ % RECONCILE_INTERVAL == 0;

        for (final Billboard billboard : this.billboardList) {
            if (!billboard.isLoaded()) {
                continue;
            }

            if (billboard instanceof final BillboardVideo billboardVideo) {
                this.advance(billboardVideo, now);
            }

            if (reconcile) {
                this.updateViewers(billboard);
            }
        }
    }

    public void play(final BillboardVideo billboardVideo) {
        billboardVideo.play();
    }

    public void stop(final BillboardVideo billboardVideo) {
        billboardVideo.stop();
    }

    public Optional<Billboard> getBillboardByIdentifier(final String identifier) {
        return Optional.ofNullable(this.billboardMap.get(identifier));
    }

    public void forget(final Player player, final boolean clearMaps) {
        final UUID uuid = player.getUniqueId();

        for (final Billboard billboard : this.billboardList) {
            billboard.getViewerSet().remove(uuid);

            if (clearMaps && billboard instanceof final BillboardImage billboardImage) {
                billboardImage.getSentSet().remove(uuid);
            }
        }
    }

    private void advance(final BillboardVideo video, final long now) {
        if (!video.isPlaying()) {
            return;
        }

        if (now - video.getNextFrameTime() > MAXIMUM_LAG) {
            video.setNextFrameTime(now);
        }

        final long interval = TimeUnit.SECONDS.toNanos(1L) / video.getFrameRate();
        final int lastIndex = video.getPatchFrameList().size() - 1;

        while (video.isPlaying() && now >= video.getNextFrameTime()) {
            final int frameIndex = video.getFrameIndex() == lastIndex ? 0 : video.getFrameIndex() + 1;
            final List<Player> viewerList = this.getViewers(video);

            final List<ClientboundMapItemDataPacket> packetList = new ArrayList<>();

            for (final BillboardPatch billboardPatch : video.getPatchFrameList().get(frameIndex)) {
                billboardPatch.apply(video.getCanvas());

                if (!viewerList.isEmpty()) {
                    packetList.add(this.createPacket(video.getMapIds()[billboardPatch.tile()], billboardPatch.x(), billboardPatch.y(), billboardPatch.width(), billboardPatch.height(), billboardPatch.colors()));
                }
            }

            for (final Player player : viewerList) {
                packetList.forEach(packet -> UtilNms.sendPacket(player, packet));
            }

            video.setFrameIndex(frameIndex);
            video.setNextFrameTime(video.getNextFrameTime() + interval);

            if (frameIndex == 0) {
                video.onLoop(viewerList);
            }

            if (frameIndex == lastIndex && !video.isLooping()) {
                video.stop();
            }
        }
    }

    private void updateViewers(final Billboard billboard) {
        final Location center = billboard.getCenter();
        final double showDistanceSquared = billboard.getViewDistance() * billboard.getViewDistance();
        final double hideDistanceSquared = (billboard.getViewDistance() + DESPAWN_MARGIN) * (billboard.getViewDistance() + DESPAWN_MARGIN);

        for (final Player player : Bukkit.getServer().getOnlinePlayers()) {
            final boolean viewing = billboard.getViewerSet().contains(player.getUniqueId());

            if (!player.getWorld().equals(center.getWorld())) {
                if (viewing) {
                    this.hide(billboard, player);
                }
                continue;
            }

            final double x = center.getX() - player.getX();
            final double y = center.getY() - player.getY();
            final double z = center.getZ() - player.getZ();
            final double distanceSquared = (x * x) + (y * y) + (z * z);

            if (!viewing && distanceSquared <= showDistanceSquared) {
                this.show(billboard, player);
            } else if (viewing && distanceSquared > hideDistanceSquared) {
                this.hide(billboard, player);
            }
        }
    }

    private void show(final Billboard billboard, final Player player) {
        final UUID uuid = player.getUniqueId();

        switch (billboard) {
            case final BillboardImage image -> {
                if (image.getSentSet().add(uuid)) {
                    for (int tile = 0; tile < image.getTileCount(); tile++) {
                        UtilNms.sendPacket(player, this.createPacket(image.getMapIds()[tile], 0, 0, UtilMap.MAP_SIZE, UtilMap.MAP_SIZE, image.getTiles()[tile]));
                    }
                }
            }
            case final BillboardVideo video -> {
                for (int tile = 0; tile < video.getTileCount(); tile++) {
                    UtilNms.sendPacket(player, this.createPacket(video.getMapIds()[tile], 0, 0, UtilMap.MAP_SIZE, UtilMap.MAP_SIZE, video.getCanvas()[tile].clone()));
                }
            }
        }

        billboard.getSpawnPacketList().forEach(packet -> UtilNms.sendPacket(player, packet));

        billboard.getViewerSet().add(uuid);
    }

    private void hide(final Billboard billboard, final Player player) {
        UtilNms.sendPacket(player, billboard.getRemovePacket());

        billboard.getViewerSet().remove(player.getUniqueId());
    }

    private List<Player> getViewers(final Billboard billboard) {
        return billboard.getViewerSet().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    private ClientboundMapItemDataPacket createPacket(final int mapId, final int x, final int y, final int width, final int height, final byte[] colors) {
        return new ClientboundMapItemDataPacket(new MapId(mapId), (byte) 0, true, Optional.empty(), Optional.of(new MapItemSavedData.MapPatch(x, y, width, height, colors)));
    }
}