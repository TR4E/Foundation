package me.trae.foundation.minecraft.paper.addon.damage.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.extensions.scheduler.annotation.Scheduler;
import me.trae.foundation.minecraft.paper.addon.damage.enums.DeathMode;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.settings.DamageAddonSettings;
import me.trae.foundation.minecraft.paper.addon.death.reason.Reason;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.utilities.UtilTime;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@Getter
@AddonSingleton
public final class DamageService {

    private final ConcurrentHashMap<UUID, CustomDamageEvent> lastDamageMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CustomDamageEvent> lastPlayerDamagerDamageMap = new ConcurrentHashMap<>();

    private final DamageAddonSettings settings;

    @Scheduler(period = 100, unit = TimeUnit.MILLISECONDS)
    public void onScheduler() {
        this.lastPlayerDamagerDamageMap.values().removeIf(this::isLastPlayerDamagerDamageExpired);
    }

    public void process(final CustomDamageEvent damageEvent) {
        UtilEvent.dispatch(damageEvent);

        if (damageEvent.isCancelled()) {
            return;
        }

        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.WEAPON));
        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.DELAY));

        if (damageEvent.isCancelled()) {
            return;
        }

        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.FREEZING));
        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.HARD_HAT));
        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.BLOCKING));
        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.ARMOUR));
        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.RESISTANCE));
        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.PROTECTION));
        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.ABSORPTION));

        if (damageEvent.isCancelled()) {
            return;
        }

        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.HEALTH));

        if (damageEvent.isCancelled()) {
            return;
        }

        UtilEvent.dispatch(new DamageAppliedEvent(damageEvent));

        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.KNOCKBACK));

        if (damageEvent.isApplied()) {
            final Entity damagee = damageEvent.getDamagee();

            this.lastDamageMap.put(damagee.getUniqueId(), damageEvent);

            if (damageEvent.getDamager() instanceof Player) {
                switch (this.settings.getDeathMode()) {
                    case LAST_PLAYER_DAMAGER -> {
                        this.lastPlayerDamagerDamageMap.put(damagee.getUniqueId(), damageEvent);
                        break;
                    }
                    case LAST_PLAYER_DAMAGER_WITH_REASON -> {
                        if (damageEvent.getReason() != null) {
                            this.lastPlayerDamagerDamageMap.put(damagee.getUniqueId(), damageEvent);
                        }
                        break;
                    }
                }
            }
        }

        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.SWEEPING));
        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.DEATH));
    }

    public boolean isLastPlayerDamagerDamageExpired(final CustomDamageEvent customDamageEvent) {
        if (this.settings.getDeathMode() == DeathMode.LAST_PLAYER_DAMAGER_WITH_REASON) {
            final Reason reason = customDamageEvent.getReason();
            if (reason != null) {
                return reason.hasExpired();
            }
        }

        return UtilTime.elapsed(customDamageEvent.getSystemTime(), this.settings.getLastPlayerDamagerDamageExpiration());
    }
}