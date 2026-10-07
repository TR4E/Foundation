package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@UtilityClass
public class UtilServer {

    public static List<Player> getOnlinePlayers() {
        return List.copyOf(Bukkit.getServer().getOnlinePlayers());
    }

    public static Optional<Player> getOnlinePlayerById(final UUID id) {
        return Optional.ofNullable(Bukkit.getServer().getPlayer(id));
    }

    public static Optional<Player> getOnlinePlayerByName(final String name) {
        return Optional.ofNullable(Bukkit.getServer().getPlayerExact(name));
    }

    public static List<OfflinePlayer> getOfflinePlayers() {
        return Stream.of(Bukkit.getServer().getOfflinePlayers()).filter(OfflinePlayer::hasPlayedBefore).toList();
    }

    public static Optional<OfflinePlayer> getOfflinePlayerById(final UUID id) {
        return Optional.of(Bukkit.getServer().getOfflinePlayer(id)).filter(OfflinePlayer::hasPlayedBefore);
    }

    public static Optional<OfflinePlayer> getOfflinePlayerByName(final String name) {
        return Optional.ofNullable(Bukkit.getServer().getOfflinePlayerIfCached(name)).filter(OfflinePlayer::hasPlayedBefore);
    }
}