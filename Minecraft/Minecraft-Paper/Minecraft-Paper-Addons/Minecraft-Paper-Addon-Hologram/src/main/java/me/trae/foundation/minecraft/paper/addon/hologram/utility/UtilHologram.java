package me.trae.foundation.minecraft.paper.addon.hologram.utility;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.paper.addon.hologram.Hologram;
import me.trae.foundation.minecraft.paper.addon.hologram.events.HologramDespawnEvent;
import me.trae.foundation.minecraft.paper.addon.hologram.events.HologramSpawnEvent;
import me.trae.foundation.minecraft.paper.addon.hologram.events.HologramUpdateEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilNms;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

@UtilityClass
public class UtilHologram {

    public static void spawn(final Player player, final Hologram hologram) {
        if (UtilEvent.supply(new HologramSpawnEvent(hologram, player)).isCancelled()) {
            return;
        }

        final Location location = hologram.getLocation();

        UtilNms.sendPacket(player, new ClientboundAddEntityPacket(
                hologram.getEntityId(),
                hologram.getEntityUuid(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getPitch(),
                location.getYaw(),
                EntityType.TEXT_DISPLAY,
                0,
                Vec3.ZERO,
                location.getYaw())
        );

        update(player, hologram);

        hologram.getViewerSet().add(player.getUniqueId());
    }

    public static void despawn(final Player player, final Hologram hologram) {
        UtilNms.sendPacket(player, new ClientboundRemoveEntitiesPacket(hologram.getEntityId()));

        hologram.getViewerSet().remove(player.getUniqueId());

        UtilEvent.dispatch(new HologramDespawnEvent(hologram, player));
    }

    public static void update(final Player player, final Hologram hologram) {
        hologram.getTextDisplay().text(hologram.getComponent(player));

        final List<SynchedEntityData.DataValue<?>> dataValueList = hologram.getHandle().getEntityData().getNonDefaultValues();
        if (dataValueList == null) {
            return;
        }

        UtilNms.sendPacket(player, new ClientboundSetEntityDataPacket(hologram.getEntityId(), dataValueList));

        UtilEvent.dispatch(new HologramUpdateEvent(hologram, player));
    }
}