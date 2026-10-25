package me.trae.foundation.minecraft.paper.plugin.framework.search.types;

import me.trae.foundation.minecraft.paper.plugin.framework.search.AbstractPaperSearchEngine;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public final class PaperOfflinePlayerSearchEngine extends AbstractPaperSearchEngine<OfflinePlayer> {

    public PaperOfflinePlayerSearchEngine() {
        super("Offline Player Search", () -> UtilServer.getOfflinePlayers().stream().filter(offlinePlayer -> offlinePlayer.getName() != null).toList());
    }

    @Override
    protected String getMatchName(final OfflinePlayer offlinePlayer, final CommandSender commandSender) {
        return offlinePlayer.getName();
    }

    @Override
    protected boolean isExact(final OfflinePlayer offlinePlayer, final String result) {
        return offlinePlayer.getName().equalsIgnoreCase(result);
    }

    @Override
    protected boolean isMatching(final OfflinePlayer offlinePlayer, final String result) {
        return offlinePlayer.getName().toLowerCase(Locale.ROOT).contains(result.toLowerCase(Locale.ROOT));
    }
}