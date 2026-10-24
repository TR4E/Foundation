package me.trae.foundation.minecraft.paper.addon.damage.listeners;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageKnockbackEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@AddonSingleton
public final class DamageKnockbackListener implements Listener {

    private static final double DEFAULT_STRENGTH = 0.4D;

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.KNOCKBACK) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !damageEvent.isApplied() || !damageEvent.isFullDamage() || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee()) || !CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        final DamageSource damageSource = CraftDamageSource.class.cast(damageEvent.getSource()).getHandle();

        if (damageSource.is(net.minecraft.tags.DamageTypeTags.NO_KNOCKBACK) || damageEvent.getBlockedDamage() > 0.0D) {
            return;
        }

        final Vec3 sourcePosition = damageSource.getSourcePosition();

        final double directionX = sourcePosition != null ? sourcePosition.x - entity.getX() : Math.random() - Math.random();
        final double directionZ = sourcePosition != null ? sourcePosition.z - entity.getZ() : Math.random() - Math.random();

        final DamageKnockbackEvent damageKnockbackEvent = UtilEvent.supply(new DamageKnockbackEvent(damageEvent, directionX, directionZ, DEFAULT_STRENGTH, false));

        if (damageKnockbackEvent.isCancelled()) {
            return;
        }

        final double x = Math.abs(damageKnockbackEvent.getDirectionX()) > 200.0D ? Math.random() - Math.random() : damageKnockbackEvent.getDirectionX();
        final double z = Math.abs(damageKnockbackEvent.getDirectionZ()) > 200.0D ? Math.random() - Math.random() : damageKnockbackEvent.getDirectionZ();

        final EntityKnockbackEvent.Cause cause = damageSource.getDirectEntity() == null ? EntityKnockbackEvent.Cause.DAMAGE : EntityKnockbackEvent.Cause.ENTITY_ATTACK;

        entity.knockback(damageKnockbackEvent.getStrength(), x, z, damageSource.getDirectEntity(), cause);

        entity.indicateDamage(x, z);

        if (damageKnockbackEvent.isSprintReset() && damageEvent.getDamager() instanceof final Player player) {
            player.setSprinting(false);
        }
    }
}