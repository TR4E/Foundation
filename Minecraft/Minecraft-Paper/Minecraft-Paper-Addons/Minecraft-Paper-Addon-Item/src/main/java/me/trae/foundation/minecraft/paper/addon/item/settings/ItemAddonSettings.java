package me.trae.foundation.minecraft.paper.addon.item.settings;

import me.trae.foundation.minecraft.paper.addon.item.DefaultItem;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.settings.AddonSettings;
import org.bukkit.Material;

public interface ItemAddonSettings extends AddonSettings {

    default DefaultItem createDefaultItem(final Material material) {
        return new DefaultItem(material);
    }
}