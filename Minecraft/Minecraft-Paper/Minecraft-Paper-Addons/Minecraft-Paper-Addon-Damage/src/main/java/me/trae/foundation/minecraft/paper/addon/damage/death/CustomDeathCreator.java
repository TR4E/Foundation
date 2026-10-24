package me.trae.foundation.minecraft.paper.addon.damage.death;

import lombok.AllArgsConstructor;
import lombok.CustomLog;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.services.DamageService;
import me.trae.foundation.minecraft.paper.addon.death.creators.DeathCreator;
import me.trae.foundation.minecraft.paper.addon.death.events.DeathEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;

@CustomLog
@AllArgsConstructor
@AddonSingleton
public final class CustomDeathCreator implements DeathCreator {

    private final DamageService damageService;

    @Override
    public DeathEvent createDeathEvent(final EntityDeathEvent entityDeathEvent) {
        final CustomDamageEvent customDamageEvent = this.damageService.getLastDamageMap().remove(entityDeathEvent.getEntity().getUniqueId());
        if (customDamageEvent == null) {
            final LivingEntity livingEntity = entityDeathEvent.getEntity();
            LOGGER.info(
                    "Skipping custom death event for entity '{}' ({}): no damage history found.",
                    livingEntity.getUniqueId(),
                    livingEntity.getType()
            );
            return null;
        }

        return new CustomDeathEvent(entityDeathEvent, customDamageEvent);
    }
}
