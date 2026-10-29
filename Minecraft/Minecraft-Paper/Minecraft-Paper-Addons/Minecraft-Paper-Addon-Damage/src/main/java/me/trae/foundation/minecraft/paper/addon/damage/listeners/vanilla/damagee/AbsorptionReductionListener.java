package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damagee.AbsorptionReductionEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.entity.Damageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.WeakHashMap;

@AddonSingleton
public final class AbsorptionReductionListener implements Listener {

    private final WeakHashMap<CustomDamageEvent, Float> pendingAbsorptionMap = new WeakHashMap<>();

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.ABSORPTION) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || damageEvent.getDamage() <= 0.0D || !Damageable.class.isInstance(damageEvent.getDamagee())) {
            return;
        }

        final Damageable damageable = Damageable.class.cast(damageEvent.getDamagee());

        final float absorption = (float) damageable.getAbsorptionAmount();
        if (absorption <= 0.0F) {
            return;
        }

        final float damage = (float) damageEvent.getDamage();

        final float absorbedDamage = Math.min(absorption, damage);

        final AbsorptionReductionEvent absorptionReductionEvent = UtilEvent.supply(new AbsorptionReductionEvent(damageEvent, absorption, absorbedDamage));

        if (damageEvent.isCancelled() || absorptionReductionEvent.isCancelled()) {
            return;
        }

        final float finalAbsorbedDamage = Math.clamp(absorptionReductionEvent.getAbsorbedDamage(), 0.0F, Math.min(absorption, damage));

        this.pendingAbsorptionMap.put(damageEvent, finalAbsorbedDamage);

        damageEvent.setAbsorbedDamage(finalAbsorbedDamage);

        damageEvent.subtractDamage(finalAbsorbedDamage);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageApplied(final DamageAppliedEvent damageAppliedEvent) {
        final CustomDamageEvent customDamageEvent = damageAppliedEvent.getDamageEvent();

        final Float absorbedDamage = this.pendingAbsorptionMap.remove(customDamageEvent);

        if (absorbedDamage == null || customDamageEvent.isCancelled()) {
            return;
        }

        final Damageable damageable = Damageable.class.cast(customDamageEvent.getDamagee());

        final double absorption = damageable.getAbsorptionAmount();

        damageable.setAbsorptionAmount(Math.max(absorption - absorbedDamage, 0.0D));

        if (damageable.getAbsorptionAmount() < absorption) {
            customDamageEvent.setApplied(true);
        }
    }
}