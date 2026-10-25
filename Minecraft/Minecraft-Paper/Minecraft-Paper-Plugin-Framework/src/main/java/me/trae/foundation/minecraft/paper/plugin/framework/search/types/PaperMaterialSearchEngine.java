package me.trae.foundation.minecraft.paper.plugin.framework.search.types;

import me.trae.foundation.minecraft.paper.plugin.framework.search.AbstractPaperSearchEngine;
import me.trae.foundation.utilities.UtilString;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.stream.Stream;

public final class PaperMaterialSearchEngine extends AbstractPaperSearchEngine<Material> {

    public PaperMaterialSearchEngine() {
        super("Material Search", () -> Stream.of(Material.values()).filter(material -> !material.isLegacy()).toList());
    }

    @Override
    protected String getMatchName(final Material material, final CommandSender commandSender) {
        return UtilString.clean(material.name());
    }

    @Override
    protected boolean isExact(final Material material, final String result) {
        return material.name().equalsIgnoreCase(result);
    }

    @Override
    protected boolean isMatching(final Material material, final String result) {
        return material.name().toLowerCase(Locale.ROOT).contains(result.toLowerCase(Locale.ROOT));
    }
}