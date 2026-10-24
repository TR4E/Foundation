package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damagee.HardHatDamageEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.tag.DamageTypeTags;

@AddonSingleton
public final class HardHatDamageListener implements Listener {

    private static final double DEFAULT_MULTIPLIER = 0.75D;

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.HARD_HAT) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !DamageTypeTags.DAMAGES_HELMET.isTagged(damageEvent.getSource().getDamageType())) {
            return;
        }

        final ItemStack[] armourContents = damageEvent.getArmourContents();
        if (armourContents == null || armourContents.length == 0) {
            return;
        }

        final ItemStack helmet = armourContents[armourContents.length - 1];
        if (helmet == null || helmet.isEmpty()) {
            return;
        }

        final HardHatDamageEvent hardHatDamageEvent = UtilEvent.supply(new HardHatDamageEvent(damageEvent, DEFAULT_MULTIPLIER));

        if (hardHatDamageEvent.isCancelled()) {
            return;
        }

        damageEvent.multiplyDamage(hardHatDamageEvent.getMultiplier());
    }
}