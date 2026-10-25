package me.trae.foundation.minecraft.velocity.plugin.framework.utility;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.experimental.UtilityClass;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.minecraft.velocity.plugin.framework.search.types.VelocityOnlinePlayerSearchEngine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@UtilityClass
public class UtilServer {

    private static final VelocityOnlinePlayerSearchEngine VELOCITY_ONLINE_PLAYER_SEARCH_ENGINE = new VelocityOnlinePlayerSearchEngine();

    public static List<Player> getOnlinePlayers() {
        return List.copyOf(Injector.INSTANCE.get(ProxyServer.class).getAllPlayers());
    }

    public static Optional<Player> getOnlinePlayerById(final UUID id) {
        return Injector.INSTANCE.get(ProxyServer.class).getPlayer(id);
    }

    public static Optional<Player> getOnlinePlayerByName(final String name) {
        return Injector.INSTANCE.get(ProxyServer.class).getPlayer(name);
    }

    public static Optional<Player> searchOnlinePlayer(final CommandSource commandSource, final String input, final boolean inform, final Predicate<Player> predicate) {
        return VELOCITY_ONLINE_PLAYER_SEARCH_ENGINE.find(commandSource, input, inform, predicate);
    }

    public static Optional<Player> searchOnlinePlayer(final CommandSource commandSource, final String input, final boolean inform) {
        return searchOnlinePlayer(commandSource, input, inform, null);
    }
}