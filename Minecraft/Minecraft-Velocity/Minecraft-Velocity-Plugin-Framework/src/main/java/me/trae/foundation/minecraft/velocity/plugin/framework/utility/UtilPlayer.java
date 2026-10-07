package me.trae.foundation.minecraft.velocity.plugin.framework.utility;

import com.velocitypowered.api.proxy.Player;
import lombok.experimental.UtilityClass;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

@UtilityClass
public class UtilPlayer {

    public static Optional<String> getIpAddress(final Player player) {
        return Optional.ofNullable(player.getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(InetAddress::getHostAddress);
    }
}