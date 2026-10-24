package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.Damageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@AddonSingleton
public final class HealthApplicationListener implements Listener {

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.HEALTH) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !Damageable.class.isInstance(damageEvent.getDamagee())) {
            return;
        }

        final Damageable damageable = Damageable.class.cast(damageEvent.getDamagee());

        final float damage = (float) damageEvent.getFinalDamage();

        if (damage <= 0.0F) {
            return;
        }

        final double previousHealth = damageable.getHealth();

        if (!CraftLivingEntity.class.isInstance(damageable)) {
            damageable.setHealth(Math.max(0.0D, previousHealth - damage));

            if (damageable.getHealth() < previousHealth) {
                damageEvent.setApplied(true);
            }

            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(damageable).getHandle();

        entity.setHealth(entity.getHealth() - damage);

        if (entity.getHealth() < previousHealth) {
            damageEvent.setApplied(true);
        }
    }
}