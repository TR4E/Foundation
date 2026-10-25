package me.trae.foundation.minecraft.paper.plugin.framework.search.types;

import me.trae.foundation.minecraft.paper.plugin.framework.PaperPlugin;
import me.trae.foundation.minecraft.paper.plugin.framework.search.AbstractPaperSearchEngine;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.registry.PluginRegistry;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public final class PaperInternalPluginSearchEngine extends AbstractPaperSearchEngine<PaperPlugin> {

    public PaperInternalPluginSearchEngine() {
        super("Internal Plugin Search", PluginRegistry::getInternalPlugins);
    }

    @Override
    protected String getMatchName(final PaperPlugin paperPlugin, final CommandSender commandSender) {
        return paperPlugin.getPluginName();
    }

    @Override
    protected boolean isExact(final PaperPlugin paperPlugin, final String result) {
        return paperPlugin.getPluginName().equalsIgnoreCase(result);
    }

    @Override
    protected boolean isMatching(final PaperPlugin paperPlugin, final String result) {
        return paperPlugin.getPluginName().toLowerCase(Locale.ROOT).contains(result.toLowerCase(Locale.ROOT));
    }
}