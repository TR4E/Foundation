package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.hoglin.HoglinBase;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

@AddonSingleton
public final class MobAttackThrowListener implements Listener {

    private static final double GOLEM_LAUNCH = 0.4D;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.KNOCKBACK) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || damageEvent.getBlockedDamage() > 0.0D || damageEvent.getCause() != DamageCause.ENTITY_ATTACK || damageEvent.getProjectile() != null) {
            return;
        }

        if (!CraftLivingEntity.class.isInstance(damageEvent.getDamager()) || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee())) {
            return;
        }

        final LivingEntity attacker = CraftLivingEntity.class.cast(damageEvent.getDamager()).getHandle();

        final LivingEntity target = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        if (HoglinBase.class.isInstance(attacker)) {
            this.resolveHoglin(attacker, target);
            return;
        }

        if (IronGolem.class.isInstance(attacker)) {
            this.resolveIronGolem(target);
        }
    }

    private void resolveHoglin(final LivingEntity attacker, final LivingEntity target) {
        if (attacker.isBaby()) {
            return;
        }

        HoglinBase.throwTarget(attacker, target);
    }

    private void resolveIronGolem(final LivingEntity target) {
        final double resistance = Math.max(0.0D, 1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));

        target.setDeltaMovement(target.getDeltaMovement().add(0.0D, GOLEM_LAUNCH * resistance, 0.0D));
    }
}