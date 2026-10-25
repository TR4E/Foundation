package me.trae.foundation.minecraft.paper.addon.npc.types;

import org.bukkit.inventory.ItemStack;

public interface EquippableNpc {

    default ItemStack getItemInMainHand() {
        return null;
    }

    default ItemStack getItemInOffHand() {
        return null;
    }

    default ItemStack[] getArmourContents() {
        return new ItemStack[0];
    }
}