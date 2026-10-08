package me.trae.foundation.minecraft.paper.addon.item.services;

import lombok.AllArgsConstructor;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.addon.item.DefaultItem;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@AddonSingleton
public final class ItemService implements Lifecycle {

    private final Map<String, CustomItem> identifierItemMap = new HashMap<>();
    private final Map<Material, CustomItem> obtainableItemMap = new EnumMap<>(Material.class);
    private final Map<Material, DefaultItem> defaultItemMap = new EnumMap<>(Material.class);

    private final List<CustomItem> items;

    @Override
    public void onLiveDependencyUpdate() {
        this.identifierItemMap.clear();
        this.obtainableItemMap.clear();

        for (final CustomItem customItem : this.items) {
            this.identifierItemMap.put(customItem.getIdentifier(), customItem);

            if (customItem.naturallyObtainable()) {
                this.obtainableItemMap.put(customItem.getMaterial(), customItem);
            }
        }
    }

    public List<CustomItem> getItems() {
        return List.copyOf(this.items);
    }

    public Optional<CustomItem> getItemByIdentifier(final String identifier) {
        return Optional.ofNullable(this.identifierItemMap.get(identifier));
    }

    public Optional<CustomItem> getObtainableItemByMaterial(final Material material) {
        return Optional.ofNullable(this.obtainableItemMap.get(material));
    }

    public Optional<CustomItem> getItemByItemStack(final ItemStack itemStack) {
        return UtilItemStack.getPersistentData(itemStack, UtilItemStack.IDENTIFIER_KEY, PersistentDataType.STRING).flatMap(this::getItemByIdentifier);
    }

    public ItemStack apply(final ItemStack itemStack) {
        if (itemStack != null && !itemStack.isEmpty()) {
            // Identifier Check
            final CustomItem identifierItem = this.getItemByItemStack(itemStack).orElse(null);
            if (identifierItem != null) {
                return identifierItem.isOutdatedByItemStack(itemStack) ? identifierItem.update(itemStack) : identifierItem.refresh(itemStack);
            }

            // Obtainable Check
            final CustomItem obtainableItem = this.getObtainableItemByMaterial(itemStack.getType()).orElse(null);
            if (obtainableItem != null) {
                return obtainableItem.create(itemStack);
            }

            // Deletable Check
            if (UtilItemStack.getPersistentData(itemStack, CustomItem.DELETABLE_KEY, PersistentDataType.BOOLEAN).orElse(false)) {
                return null;
            }

            // Default Item Check
            final DefaultItem defaultItem = this.defaultItemMap.computeIfAbsent(itemStack.getType(), DefaultItem::new);

            final boolean isOldCustomItem = UtilItemStack.getPersistentData(itemStack, UtilItemStack.IDENTIFIER_KEY, PersistentDataType.STRING).isPresent() || UtilItemStack.getPersistentData(itemStack, CustomItem.VERSION_KEY, PersistentDataType.STRING).isPresent();

            return isOldCustomItem ? defaultItem.create(itemStack) : defaultItem.refresh(itemStack);
        }

        return itemStack;
    }

    public void updateInventory(final Inventory inventory) {
        final ItemStack[] contents = inventory.getContents();

        for (int i = 0; i < contents.length; i++) {
            final ItemStack existingItemStack = contents[i];
            final ItemStack updatedItemStack = this.apply(existingItemStack);

            if (updatedItemStack != existingItemStack) {
                inventory.setItem(i, updatedItemStack);
            }
        }
    }
}