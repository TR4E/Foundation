package me.trae.foundation.minecraft.paper.addon.damage.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
@AddonSingleton
public final class DamageService {

    @Getter
    private final ConcurrentHashMap<UUID, CustomDamageEvent> lastDamageMap = new ConcurrentHashMap<>();

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
            this.lastDamageMap.put(damageEvent.getDamagee().getUniqueId(), damageEvent);
        }

        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.SWEEPING));
        UtilEvent.dispatch(new DamageSetupEvent(damageEvent, Stage.DEATH));
    }
}