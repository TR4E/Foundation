package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.paper.plugin.framework.search.types.PaperOfflinePlayerSearchEngine;
import me.trae.foundation.minecraft.paper.plugin.framework.search.types.PaperOnlinePlayerSearchEngine;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

@UtilityClass
public class UtilServer {

    private static final PaperOnlinePlayerSearchEngine PAPER_ONLINE_PLAYER_SEARCH_ENGINE = new PaperOnlinePlayerSearchEngine();
    private static final PaperOfflinePlayerSearchEngine PAPER_OFFLINE_PLAYER_SEARCH_ENGINE = new PaperOfflinePlayerSearchEngine();

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

    public static Optional<Player> searchOnlinePlayer(final CommandSender commandSender, final String input, final boolean inform, final Predicate<Player> predicate) {
        return PAPER_ONLINE_PLAYER_SEARCH_ENGINE.find(commandSender, input, inform, predicate);
    }

    public static Optional<Player> searchOnlinePlayer(final CommandSender commandSender, final String input, final boolean inform) {
        return searchOnlinePlayer(commandSender, input, inform, null);
    }

    public static Optional<OfflinePlayer> searchOfflinePlayer(final CommandSender commandSender, final String input, final boolean inform, final Predicate<OfflinePlayer> predicate) {
        return PAPER_OFFLINE_PLAYER_SEARCH_ENGINE.find(commandSender, input, inform, predicate);
    }

    public static Optional<OfflinePlayer> searchOfflinePlayer(final CommandSender commandSender, final String input, final boolean inform) {
        return searchOfflinePlayer(commandSender, input, inform, null);
    }
}