package me.trae.foundation.minecraft.paper.addon.item.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.addon.item.DefaultItem;
import me.trae.foundation.minecraft.paper.addon.item.events.VanillaItemStackUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.item.search.types.CustomItemSearchEngine;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@AllArgsConstructor
@Getter
@AddonSingleton
public final class ItemService implements Lifecycle {

    private final CustomItemSearchEngine customItemSearchEngine = new CustomItemSearchEngine(this);

    private final Map<String, CustomItem> uniqueIdItemMap = new HashMap<>();
    private final Map<Material, CustomItem> obtainableItemMap = new EnumMap<>(Material.class);
    private final Map<Material, DefaultItem> defaultItemMap = new EnumMap<>(Material.class);

    private final List<CustomItem> itemList;

    @Override
    public void onLiveDependencyUpdate() {
        this.uniqueIdItemMap.clear();
        this.obtainableItemMap.clear();

        for (final CustomItem customItem : this.itemList) {
            this.uniqueIdItemMap.put(customItem.getUniqueId(), customItem);

            if (customItem.naturallyObtainable()) {
                this.obtainableItemMap.put(customItem.getMaterial(), customItem);
            }
        }
    }

    public Optional<CustomItem> getItemByUniqueId(final String uniqueId) {
        if (uniqueId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(this.uniqueIdItemMap.get(uniqueId));
    }

    public Optional<CustomItem> getObtainableItemByMaterial(final Material material) {
        if (material == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(this.obtainableItemMap.get(material));
    }

    public Optional<CustomItem> getItemByItemStack(final ItemStack itemStack) {
        if (itemStack == null) {
            return Optional.empty();
        }

        return UtilItemStack.getPersistentData(itemStack, UtilItemStack.UNIQUE_ID_KEY, PersistentDataType.STRING).flatMap(this::getItemByUniqueId);
    }

    public ItemStack apply(final ItemStack itemStack) {
        if (itemStack != null && !itemStack.isEmpty()) {
            // Unique ID Check
            final CustomItem customItem = this.getItemByItemStack(itemStack).orElse(null);
            if (customItem != null) {
                return this.touch(customItem.isOutdatedByItemStack(itemStack) ? customItem.update(itemStack) : customItem.refresh(itemStack));
            }

            // Obtainable Check
            final CustomItem obtainableItem = this.getObtainableItemByMaterial(itemStack.getType()).orElse(null);
            if (obtainableItem != null) {
                return this.touch(obtainableItem.create(itemStack));
            }

            // Deletable Check
            if (UtilItemStack.getPersistentData(itemStack, CustomItem.DELETABLE_KEY, PersistentDataType.BOOLEAN).orElse(false)) {
                return null;
            }

            // Default Item Check
            final DefaultItem defaultItem = this.defaultItemMap.computeIfAbsent(itemStack.getType(), DefaultItem::new);

            final boolean wasCustomItem = UtilItemStack.getPersistentData(itemStack, UtilItemStack.UNIQUE_ID_KEY, PersistentDataType.STRING).isPresent() || UtilItemStack.getPersistentData(itemStack, CustomItem.VERSION_KEY, PersistentDataType.STRING).isPresent();

            return this.touch(wasCustomItem ? defaultItem.create(itemStack) : defaultItem.refresh(itemStack));
        }

        return itemStack;
    }

    public Optional<CustomItem> searchCustomItem(final CommandSender commandSender, final String input, final boolean inform, final Predicate<CustomItem> predicate) {
        return this.customItemSearchEngine.find(commandSender, input, inform, predicate);
    }

    public Optional<CustomItem> searchCustomItem(final CommandSender commandSender, final String input, final boolean inform) {
        return this.searchCustomItem(commandSender, input, inform, null);
    }

    private ItemStack touch(final ItemStack itemStack) {
        return UtilEvent.supply(new VanillaItemStackUpdateEvent(itemStack)).getItemStack();
    }
}