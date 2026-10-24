package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damagee.BlockingDamageEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@AddonSingleton
public final class BlockingDamageListener implements Listener {

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.BLOCKING) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !damageEvent.hasDamage() || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee()) || !CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return;
        }

        final LivingEntity livingEntity = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        final DamageSource damageSource = CraftDamageSource.class.cast(damageEvent.getSource()).getHandle();

        final float damage = (float) damageEvent.getDamage();

        if (!livingEntity.canBlockAttack(damageSource, damage)) {
            return;
        }

        final BlockingDamageEvent blockingDamageEvent = UtilEvent.supply(new BlockingDamageEvent(damageEvent, livingEntity.resolveBlockedDamage(damageSource, damage)));

        if (blockingDamageEvent.isCancelled()) {
            return;
        }

        final double blockedDamage = Math.clamp(blockingDamageEvent.getBlockedDamage(), 0.0D, damageEvent.getFinalDamage());

        damageEvent.setBlockedDamage(blockedDamage);

        damageEvent.subtractDamage(blockedDamage);
    }
}