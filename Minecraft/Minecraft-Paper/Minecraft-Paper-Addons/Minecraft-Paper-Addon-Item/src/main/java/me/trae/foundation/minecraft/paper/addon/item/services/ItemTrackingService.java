package me.trae.foundation.minecraft.paper.addon.item.services;

import lombok.AllArgsConstructor;
import lombok.CustomLog;
import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@CustomLog
@AllArgsConstructor
@AddonSingleton
public final class ItemTrackingService {

    private final ItemService itemService;

    public Optional<String> getTrackingIdByItemStack(final ItemStack itemStack) {
        if (this.itemService.getItemByItemStack(itemStack).map(CustomItem::isTrackable).orElse(false)) {
            try {
                return UtilItemStack.getPersistentData(itemStack, CustomItem.TRACKING_ID_KEY, PersistentDataType.STRING);
            } catch (final Exception exception) {
                LOGGER.info("Failed to get Tracking Id by ItemStack: <{}>", exception.getMessage());
            }
        }

        return Optional.empty();
    }

    public Set<String> findDuplicateTrackingIdSet(final ItemStack[] contents) {
        final Set<String> seenIdSet = new HashSet<>();
        final Set<String> duplicatedIdSet = new HashSet<>();

        for (final ItemStack content : contents) {
            if (content == null || content.isEmpty()) {
                continue;
            }

            final String id = this.getTrackingIdByItemStack(content).orElse(null);
            if (id == null) {
                continue;
            }

            if (content.getAmount() > 1 || !seenIdSet.add(id)) {
                duplicatedIdSet.add(id);
            }
        }

        return Set.copyOf(duplicatedIdSet);
    }
}