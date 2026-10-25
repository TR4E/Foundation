package me.trae.foundation.minecraft.paper.plugin.framework.search.types;

import me.trae.foundation.minecraft.paper.plugin.framework.search.AbstractPaperSearchEngine;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public final class PaperWorldSearchEngine extends AbstractPaperSearchEngine<World> {

    public PaperWorldSearchEngine() {
        super("World Search", () -> Bukkit.getServer().getWorlds());
    }

    @Override
    protected String getMatchName(final World world, final CommandSender commandSender) {
        return world.getName();
    }

    @Override
    protected boolean isExact(final World world, final String result) {
        return world.getName().equalsIgnoreCase(result);
    }

    @Override
    protected boolean isMatching(final World world, final String result) {
        return world.getName().toLowerCase(Locale.ROOT).contains(result.toLowerCase(Locale.ROOT));
    }
}