package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damagee.FreezingDamageEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.tag.DamageTypeTags;

@AddonSingleton
public final class FreezingDamageListener implements Listener {

    private static final double DEFAULT_MULTIPLIER = 5.0D;

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.FREEZING) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !DamageTypeTags.IS_FREEZING.isTagged(damageEvent.getSource().getDamageType())) {
            return;
        }

        if (!Tag.ENTITY_TYPES_FREEZE_HURTS_EXTRA_TYPES.isTagged(damageEvent.getDamagee().getType())) {
            return;
        }

        final FreezingDamageEvent freezingDamageEvent = UtilEvent.supply(new FreezingDamageEvent(damageEvent, DEFAULT_MULTIPLIER));

        if (freezingDamageEvent.isCancelled()) {
            return;
        }

        damageEvent.multiplyDamage(freezingDamageEvent.getMultiplier());
    }
}