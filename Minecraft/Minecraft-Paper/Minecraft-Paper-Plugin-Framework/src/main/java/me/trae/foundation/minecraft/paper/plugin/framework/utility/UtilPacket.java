package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import net.minecraft.network.protocol.Packet;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

@UtilityClass
public class UtilPacket {

    public static void send(final Player player, final Packet<?> packet) {
        if (!(player instanceof final CraftPlayer craftPlayer)) {
            return;
        }

        if (!craftPlayer.isOnline()) {
            return;
        }

        craftPlayer.getHandle().connection.send(packet);
    }
}