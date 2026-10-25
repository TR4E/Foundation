package me.trae.foundation.minecraft.paper.addon.npc.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.npc.Npc;
import me.trae.foundation.minecraft.paper.addon.npc.services.NpcService;
import me.trae.foundation.minecraft.paper.addon.npc.types.DamageableNpc;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;

@AllArgsConstructor
@AddonSingleton
public final class NpcDamageListener implements Listener {

    private final NpcService npcService;

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Entity entity = event.getEntity();

        if (!this.isProtected(entity)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityCombust(final EntityCombustEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Entity entity = event.getEntity();

        if (!this.isProtected(entity)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityPotionEffect(final EntityPotionEffectEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED) {
            return;
        }

        final PotionEffect newEffect = event.getNewEffect();
        if (newEffect == null) {
            return;
        }

        if (newEffect.getType().getCategory() != PotionEffectTypeCategory.HARMFUL) {
            return;
        }

        final Entity entity = event.getEntity();

        if (!this.isProtected(entity)) {
            return;
        }

        event.setCancelled(true);
    }

    private boolean isProtected(final Entity entity) {
        final Npc<?> npc = this.npcService.getNpcByEntity(entity).orElse(null);
        if (npc == null) {
            return false;
        }

        if (npc instanceof DamageableNpc) {
            return false;
        }

        return true;
    }
}