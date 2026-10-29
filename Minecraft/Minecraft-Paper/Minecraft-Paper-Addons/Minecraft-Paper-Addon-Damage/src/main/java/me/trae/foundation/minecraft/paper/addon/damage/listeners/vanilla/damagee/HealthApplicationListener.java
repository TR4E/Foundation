package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.Damageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason;

@AddonSingleton
public final class HealthApplicationListener implements Listener {

    private static final float STATISTIC_SCALE = 10.0F;

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

        final DamageSource damageSource = CraftDamageSource.class.isInstance(damageEvent.getSource()) ? CraftDamageSource.class.cast(damageEvent.getSource()).getHandle() : null;

        if (Player.class.isInstance(entity)) {
            final Player player = Player.class.cast(entity);

            if (damageSource != null) {
                player.causeFoodExhaustion(damageSource.getFoodExhaustion(), ExhaustionReason.DAMAGED);
            }

            if (damage < Float.MAX_VALUE) {
                player.awardStat(Stats.DAMAGE_TAKEN, Math.round(damage * STATISTIC_SCALE));
            }
        }

        if (damageSource != null) {
            entity.getCombatTracker().recordDamage(damageSource, damage);
        }

        entity.setHealth(entity.getHealth() - damage);

        entity.gameEvent(GameEvent.ENTITY_DAMAGE);

        if (entity.getHealth() < previousHealth) {
            damageEvent.setApplied(true);
        }
    }
}