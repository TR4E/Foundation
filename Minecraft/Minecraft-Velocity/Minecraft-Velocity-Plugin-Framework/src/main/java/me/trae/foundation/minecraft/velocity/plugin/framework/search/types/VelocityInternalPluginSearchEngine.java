package me.trae.foundation.minecraft.velocity.plugin.framework.search.types;

import com.velocitypowered.api.command.CommandSource;
import me.trae.foundation.minecraft.velocity.plugin.framework.VelocityPlugin;
import me.trae.foundation.minecraft.velocity.plugin.framework.search.AbstractVelocitySearchEngine;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.registry.PluginRegistry;

import java.util.Locale;

public final class VelocityInternalPluginSearchEngine extends AbstractVelocitySearchEngine<VelocityPlugin> {

    public VelocityInternalPluginSearchEngine() {
        super("Internal Plugin Search", PluginRegistry::getInternalPlugins);
    }

    @Override
    protected String getMatchName(final VelocityPlugin velocityPlugin, final CommandSource commandSource) {
        return velocityPlugin.getPluginName();
    }

    @Override
    protected boolean isExact(final VelocityPlugin velocityPlugin, final String result) {
        return velocityPlugin.getPluginName().equalsIgnoreCase(result);
    }

    @Override
    protected boolean isMatching(final VelocityPlugin velocityPlugin, final String result) {
        return velocityPlugin.getPluginName().toLowerCase(Locale.ROOT).contains(result.toLowerCase(Locale.ROOT));
    }
}