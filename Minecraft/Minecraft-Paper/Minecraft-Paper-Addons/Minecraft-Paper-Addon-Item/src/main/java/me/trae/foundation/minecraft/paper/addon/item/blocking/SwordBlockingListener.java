package me.trae.foundation.minecraft.paper.addon.item.blocking;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.item.events.VanillaItemStackUpdateEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Applies the {@code BLOCKS_ATTACKS} data component to vanilla swords so they can block attacks,
 * giving them 1.8-style blocking behaviour and letting a resource pack's {@code using_item} model
 * condition fire while blocking.
 *
 * <p>The component is written onto the item stack, so it is part of that stack's data and persists
 * independently of this listener. Removing this addon does not disable blocking on swords that have
 * already been stamped: they keep the component, and their holders keep blocking, until something
 * explicitly resets it.
 *
 * <p>To remove it cleanly, set {@link #setEnabled(boolean)} to {@code false} and let an inventory
 * synchronization pass run while this listener is still registered. That resets the component on
 * every sword the pass touches, including stacks held in containers and ender chests. Pulling the
 * addon without that step leaves stamped swords blocking permanently.
 */
@AddonSingleton
public final class SwordBlockingListener implements Listener {

    private static final Set<Material> MATERIAL_SET = Set.of(
            Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.IRON_SWORD,
            Material.COPPER_SWORD, Material.STONE_SWORD, Material.GOLDEN_SWORD,
            Material.WOODEN_SWORD
    );

    @Getter
    @Setter
    private static boolean enabled = false;

    @SuppressWarnings("UnstableApiUsage")
    @EventHandler
    public void onVanillaItemStackUpdate(final VanillaItemStackUpdateEvent event) {
        final ItemStack itemStack = event.getItemStack();

        if (!MATERIAL_SET.contains(itemStack.getType())) {
            return;
        }

        if (!enabled) {
            itemStack.resetData(DataComponentTypes.BLOCKS_ATTACKS);
            return;
        }

        itemStack.setData(DataComponentTypes.BLOCKS_ATTACKS, BlocksAttacks.blocksAttacks()
                .blockDelaySeconds(0.05F)
                .disableCooldownScale(0.0F)
                .damageReductions(List.of())
                .build());
    }
}