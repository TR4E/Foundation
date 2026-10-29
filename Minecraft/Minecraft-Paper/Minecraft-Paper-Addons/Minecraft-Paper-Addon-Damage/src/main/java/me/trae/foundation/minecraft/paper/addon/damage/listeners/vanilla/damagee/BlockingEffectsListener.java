package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.BlocksAttacks;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@AddonSingleton
public final class BlockingEffectsListener implements Listener {

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.BLOCKING_EFFECTS) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || damageEvent.getBlockedDamage() <= 0.0D || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee()) || !CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        final ServerLevel serverLevel = ServerLevel.class.cast(entity.level());

        final DamageSource damageSource = CraftDamageSource.class.cast(damageEvent.getSource()).getHandle();

        final BlocksAttacks blocksAttacks = entity.getUseItem().get(DataComponents.BLOCKS_ATTACKS);

        entity.blockingItemEffects(serverLevel, damageSource, (float) damageEvent.getBlockedDamage());

        if (blocksAttacks != null && damageEvent.isFullDamage()) {
            blocksAttacks.onBlocked(serverLevel, entity);
        }
    }
}