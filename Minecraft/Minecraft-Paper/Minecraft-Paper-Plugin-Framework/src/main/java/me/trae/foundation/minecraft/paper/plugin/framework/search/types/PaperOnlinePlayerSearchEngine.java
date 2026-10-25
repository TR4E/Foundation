package me.trae.foundation.minecraft.paper.plugin.framework.search.types;

import me.trae.foundation.minecraft.paper.plugin.framework.search.AbstractPaperSearchEngine;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class PaperOnlinePlayerSearchEngine extends AbstractPaperSearchEngine<Player> {

    public PaperOnlinePlayerSearchEngine() {
        super("Online Player Search", UtilServer::getOnlinePlayers);
    }

    @Override
    protected String getMatchName(final Player player, final CommandSender commandSender) {
        return player.getName();
    }

    @Override
    protected boolean isExact(final Player player, final String result) {
        return player.getName().equalsIgnoreCase(result);
    }

    @Override
    protected boolean isMatching(final Player player, final String result) {
        return player.getName().toLowerCase(Locale.ROOT).contains(result.toLowerCase(Locale.ROOT));
    }
}