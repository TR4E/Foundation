package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import io.papermc.paper.adventure.PaperAdventure;
import lombok.experimental.UtilityClass;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

@UtilityClass
public class UtilNms {

    public static Component toNms(final net.kyori.adventure.text.Component component) {
        return PaperAdventure.asVanilla(component);
    }

    public static void sendPacket(final Player player, final Packet<?> packet) {
        if (player instanceof final CraftPlayer craftPlayer && craftPlayer.isOnline()) {
            craftPlayer.getHandle().connection.send(packet);
        }
    }
}