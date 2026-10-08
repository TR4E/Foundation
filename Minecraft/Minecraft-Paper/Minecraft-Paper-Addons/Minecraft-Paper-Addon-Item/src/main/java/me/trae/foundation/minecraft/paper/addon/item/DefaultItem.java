package me.trae.foundation.minecraft.paper.addon.item;

import org.bukkit.Material;

import java.util.List;

public final class DefaultItem extends Item {

    public DefaultItem(final Material material) {
        super(material);
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public List<String> getLore() {
        return null;
    }
}