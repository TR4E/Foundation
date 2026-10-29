package me.trae.foundation.minecraft.paper.addon.damage.listeners;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageKnockbackEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@AddonSingleton
public final class DamageKnockbackListener implements Listener {

    private static final double DEFAULT_STRENGTH = 0.4D;

    private static final double MAXIMUM_DIRECTION = 200.0D;

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.KNOCKBACK) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !damageEvent.isFullDamage() || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee()) || !CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        final DamageSource damageSource = CraftDamageSource.class.cast(damageEvent.getSource()).getHandle();

        if (damageSource.is(DamageTypeTags.NO_KNOCKBACK)) {
            return;
        }

        final Entity directEntity = damageSource.getDirectEntity();

        final Vec3 sourcePosition = damageSource.getSourcePosition();

        double directionX = 0.0D;
        double directionZ = 0.0D;

        if (Projectile.class.isInstance(directEntity)) {
            final DoubleDoubleImmutablePair direction = Projectile.class.cast(directEntity).calculateHorizontalHurtKnockbackDirection(entity, damageSource);

            directionX = -direction.leftDouble();
            directionZ = -direction.rightDouble();
        } else if (sourcePosition != null) {
            directionX = sourcePosition.x() - entity.getX();
            directionZ = sourcePosition.z() - entity.getZ();
        }

        final DamageKnockbackEvent damageKnockbackEvent = UtilEvent.supply(new DamageKnockbackEvent(damageEvent, directionX, directionZ, DEFAULT_STRENGTH, false));

        if (damageKnockbackEvent.isCancelled()) {
            return;
        }

        final double x = Math.abs(damageKnockbackEvent.getDirectionX()) > MAXIMUM_DIRECTION ? Math.random() - Math.random() : damageKnockbackEvent.getDirectionX();
        final double z = Math.abs(damageKnockbackEvent.getDirectionZ()) > MAXIMUM_DIRECTION ? Math.random() - Math.random() : damageKnockbackEvent.getDirectionZ();

        entity.knockback(damageKnockbackEvent.getStrength(), x, z, directEntity, directEntity == null ? EntityKnockbackEvent.Cause.DAMAGE : EntityKnockbackEvent.Cause.ENTITY_ATTACK);

        if (damageEvent.getBlockedDamage() <= 0.0D) {
            entity.indicateDamage(x, z);
        }

        if (damageKnockbackEvent.isSprintReset() && damageEvent.getDamager() instanceof final Player player) {
            player.setSprinting(false);
        }
    }
}