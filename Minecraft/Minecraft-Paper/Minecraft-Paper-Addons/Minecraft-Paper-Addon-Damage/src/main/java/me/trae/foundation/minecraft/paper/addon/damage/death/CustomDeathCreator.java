package me.trae.foundation.minecraft.paper.addon.damage.death;

import lombok.AllArgsConstructor;
import lombok.CustomLog;
import me.trae.foundation.minecraft.paper.addon.damage.enums.DeathMode;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.services.DamageService;
import me.trae.foundation.minecraft.paper.addon.death.creators.DeathCreator;
import me.trae.foundation.minecraft.paper.addon.death.events.DeathEvent;
import me.trae.foundation.minecraft.paper.addon.death.settings.DeathAddonSettings;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;

@CustomLog
@AllArgsConstructor
@AddonSingleton
public final class CustomDeathCreator implements DeathCreator {

    private final DamageService damageService;

    @Override
    public DeathEvent createDeathEvent(final EntityDeathEvent entityDeathEvent, final DeathAddonSettings deathAddonSettings) {
        final LivingEntity entity = entityDeathEvent.getEntity();

        final CustomDamageEvent originalCustomDamageEvent = this.damageService.getLastDamageMap().remove(entity.getUniqueId());
        if (originalCustomDamageEvent == null) {
            LOGGER.info("Skipping custom death event for entity '{}' ({}): no damage history found.", entity.getUniqueId(), entity.getType());
            return null;
        }

        final CustomDamageEvent customDamageEvent = this.getCustomDamageEvent(entity);

        return new CustomDeathEvent(
                entityDeathEvent,
                originalCustomDamageEvent,
                customDamageEvent != null ? customDamageEvent : originalCustomDamageEvent,
                deathAddonSettings
        );
    }

    private CustomDamageEvent getCustomDamageEvent(final Entity entity) {
        if (this.damageService.getSettings().getDeathMode() == DeathMode.NORMAL) {
            return null;
        }

        final CustomDamageEvent customDamageEvent = this.damageService.getLastPlayerDamagerDamageMap().remove(entity.getUniqueId());
        if (customDamageEvent == null) {
            return null;
        }

        return this.damageService.isLastPlayerDamagerDamageExpired(customDamageEvent) ? null : customDamageEvent;
    }
}