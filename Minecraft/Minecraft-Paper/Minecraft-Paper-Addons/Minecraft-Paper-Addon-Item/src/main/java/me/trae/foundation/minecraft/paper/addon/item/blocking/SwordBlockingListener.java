package me.trae.foundation.minecraft.paper.addon.item.blocking;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.events.VanillaItemStackUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

@AllArgsConstructor
@AddonSingleton
public final class SwordBlockingListener implements Listener {

    private static final Set<Material> MATERIAL_SET = Set.of(
            Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.IRON_SWORD,
            Material.COPPER_SWORD, Material.STONE_SWORD, Material.GOLDEN_SWORD,
            Material.WOODEN_SWORD
    );

    private final ItemService itemService;

    @SuppressWarnings("UnstableApiUsage")
    @EventHandler
    public void onVanillaItemStackUpdate(final VanillaItemStackUpdateEvent event) {
        final ItemStack itemStack = event.getItemStack();

        if (!MATERIAL_SET.contains(itemStack.getType())) {
            return;
        }

        if (!this.itemService.getSettings().enableSwordBlocking()) {
            if (itemStack.hasData(DataComponentTypes.BLOCKS_ATTACKS)) {
                itemStack.resetData(DataComponentTypes.BLOCKS_ATTACKS);
            }
            return;
        }

        if (!itemStack.hasData(DataComponentTypes.BLOCKS_ATTACKS)) {
            itemStack.setData(DataComponentTypes.BLOCKS_ATTACKS, BlocksAttacks.blocksAttacks()
                    .blockDelaySeconds(0.05F)
                    .disableCooldownScale(0.0F)
                    .damageReductions(List.of())
                    .build());
        }
    }
}