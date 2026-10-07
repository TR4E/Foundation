package me.trae.foundation.minecraft.velocity.plugin.framework.utility;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.experimental.UtilityClass;
import me.trae.foundation.injector.api.Injector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@UtilityClass
public class UtilServer {

    public static List<Player> getOnlinePlayers() {
        return List.copyOf(Injector.INSTANCE.get(ProxyServer.class).getAllPlayers());
    }

    public static Optional<Player> getOnlinePlayerById(final UUID id) {
        return Injector.INSTANCE.get(ProxyServer.class).getPlayer(id);
    }

    public static Optional<Player> getOnlinePlayerByName(final String name) {
        return Injector.INSTANCE.get(ProxyServer.class).getPlayer(name);
    }
}