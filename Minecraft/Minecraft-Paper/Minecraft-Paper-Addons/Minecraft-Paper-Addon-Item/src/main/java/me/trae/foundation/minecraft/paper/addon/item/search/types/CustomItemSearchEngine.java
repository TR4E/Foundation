package me.trae.foundation.minecraft.paper.addon.item.search.types;

import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.plugin.framework.search.AbstractPaperSearchEngine;
import org.bukkit.command.CommandSender;

import java.awt.Color;
import java.util.Locale;

public final class CustomItemSearchEngine extends AbstractPaperSearchEngine<CustomItem> {

    public CustomItemSearchEngine(final ItemService itemService) {
        super("Custom Item Search", itemService::getItemList);
    }

    @Override
    protected String getMatchName(final CustomItem customItem, final CommandSender commandSender) {
        return customItem.getName();
    }

    @Override
    protected boolean isExact(final CustomItem customItem, final String result) {
        return customItem.getNamespace().equalsIgnoreCase(result);
    }

    @Override
    protected boolean isMatching(final CustomItem customItem, final String result) {
        return customItem.getNamespace().toLowerCase(Locale.ROOT).contains(result.toLowerCase(Locale.ROOT));
    }

    @Override
    protected Color getTypeColor(final CustomItem customItem, final CommandSender commandSender) {
        return customItem.getColor();
    }
}