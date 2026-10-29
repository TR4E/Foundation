package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

@AddonSingleton
public final class AggressionListener implements Listener {

    private static final int MEMORY_TIME = 100;

    @EventHandler(priority = EventPriority.LOW)
    public void onDamageApplied(final DamageAppliedEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee()) || !CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        this.resolveMobResponsible(entity, CraftDamageSource.class.cast(damageEvent.getSource()).getHandle(), damageEvent.getDamager());

        this.resolvePlayerResponsible(entity, damageEvent.getDamager());

        this.resolveLastHurtMob(damageEvent, entity);
    }

    private void resolveMobResponsible(final LivingEntity entity, final DamageSource damageSource, final Entity damager) {
        if (!CraftLivingEntity.class.isInstance(damager) || damageSource.is(DamageTypeTags.NO_ANGER)) {
            return;
        }

        if (damageSource.is(DamageTypes.WIND_CHARGE) && entity.getType().is(EntityTypeTags.NO_ANGER_FROM_WIND_CHARGE)) {
            return;
        }

        entity.setLastHurtByMob(CraftLivingEntity.class.cast(damager).getHandle());
    }

    private void resolvePlayerResponsible(final LivingEntity entity, final Entity damager) {
        if (CraftPlayer.class.isInstance(damager)) {
            entity.setLastHurtByPlayer(CraftPlayer.class.cast(damager).getHandle(), MEMORY_TIME);
            return;
        }

        if (!Wolf.class.isInstance(damager) || !Wolf.class.cast(damager).isTamed()) {
            return;
        }

        final UUID ownerId = Wolf.class.cast(damager).getOwnerUniqueId();

        if (ownerId == null) {
            entity.lastHurtByPlayer = null;
            entity.lastHurtByPlayerMemoryTime = 0;
            return;
        }

        entity.setLastHurtByPlayer(ownerId, MEMORY_TIME);
    }

    private void resolveLastHurtMob(final CustomDamageEvent damageEvent, final LivingEntity entity) {
        if (damageEvent.getBlockedDamage() > 0.0D || !CraftLivingEntity.class.isInstance(damageEvent.getDamager())) {
            return;
        }

        CraftLivingEntity.class.cast(damageEvent.getDamager()).getHandle().setLastHurtMob(entity);
    }
}