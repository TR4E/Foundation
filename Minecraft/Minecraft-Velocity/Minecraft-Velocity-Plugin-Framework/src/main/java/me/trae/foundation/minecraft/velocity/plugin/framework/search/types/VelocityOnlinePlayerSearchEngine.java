package me.trae.foundation.minecraft.velocity.plugin.framework.search.types;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.trae.foundation.minecraft.velocity.plugin.framework.search.AbstractVelocitySearchEngine;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.UtilServer;

import java.util.Locale;

public final class VelocityOnlinePlayerSearchEngine extends AbstractVelocitySearchEngine<Player> {

    public VelocityOnlinePlayerSearchEngine() {
        super("Online Player Search", UtilServer::getOnlinePlayers);
    }

    @Override
    protected String getMatchName(final Player player, final CommandSource commandSource) {
        return player.getUsername();
    }

    @Override
    protected boolean isExact(final Player player, final String result) {
        return player.getUsername().equalsIgnoreCase(result);
    }

    @Override
    protected boolean isMatching(final Player player, final String result) {
        return player.getUsername().toLowerCase(Locale.ROOT).contains(result.toLowerCase(Locale.ROOT));
    }
}