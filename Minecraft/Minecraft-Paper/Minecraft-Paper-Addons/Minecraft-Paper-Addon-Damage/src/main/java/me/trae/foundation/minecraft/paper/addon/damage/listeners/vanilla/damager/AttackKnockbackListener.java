package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageKnockbackEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackSnapshotListener.AttackSnapshot;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.hoglin.HoglinBase;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.WeakHashMap;

@AllArgsConstructor
@AddonSingleton
public final class AttackKnockbackListener implements Listener {

    private static final float STRONG_STRENGTH = 0.9F;

    private static final double SPRINT_KNOCKBACK = 0.5D;

    private static final double SELF_DAMPING = 0.6D;

    private final WeakHashMap<CustomDamageEvent, Vec3> movementMap = new WeakHashMap<>();
    private final WeakHashMap<CustomDamageEvent, Double> strengthMap = new WeakHashMap<>();

    private final AttackSnapshotListener attackSnapshotListener;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomDamage(final CustomDamageEvent event) {
        if (!this.isAttackKnockback(event)) {
            return;
        }

        this.movementMap.put(event, CraftLivingEntity.class.cast(event.getDamagee()).getHandle().getDeltaMovement());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamageKnockback(final DamageKnockbackEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (event.isCancelled() || !this.isAttackKnockback(damageEvent)) {
            return;
        }

        final LivingEntity attacker = CraftLivingEntity.class.cast(damageEvent.getDamager()).getHandle();

        double strength = this.getKnockback(attacker, CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle(), CraftDamageSource.class.cast(damageEvent.getSource()).getHandle());

        if (ServerPlayer.class.isInstance(attacker)) {
            final AttackSnapshot attackSnapshot = this.attackSnapshotListener.getSnapshot(damageEvent);

            if (attackSnapshot == null) {
                return;
            }

            if (attackSnapshot.sprinting() && attackSnapshot.strength() > STRONG_STRENGTH) {
                strength += SPRINT_KNOCKBACK;
            }
        }

        if (strength <= 0.0D) {
            return;
        }

        this.strengthMap.put(damageEvent, strength);

        event.setSprintReset(ServerPlayer.class.isInstance(attacker));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.KNOCKBACK) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        final Double strength = this.strengthMap.remove(damageEvent);

        final Vec3 currentMovement = this.movementMap.remove(damageEvent);

        if (strength == null || damageEvent.isCancelled()) {
            return;
        }

        final LivingEntity attacker = CraftLivingEntity.class.cast(damageEvent.getDamager()).getHandle();

        final LivingEntity target = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        final double yaw = attacker.getYRot() * (Math.PI / 180.0D);

        target.knockback(strength, Mth.sin(yaw), -Mth.cos(yaw), attacker, EntityKnockbackEvent.Cause.ENTITY_ATTACK);

        attacker.setDeltaMovement(attacker.getDeltaMovement().multiply(SELF_DAMPING, 1.0D, SELF_DAMPING));

        if (!ServerPlayer.class.isInstance(attacker) || !ServerPlayer.class.isInstance(target) || !target.hurtMarked || currentMovement == null) {
            return;
        }

        ServerPlayer.class.cast(target).connection.send(new ClientboundSetEntityMotionPacket(target));

        target.hurtMarked = false;

        target.setDeltaMovement(currentMovement);
    }

    private double getKnockback(final LivingEntity attacker, final LivingEntity target, final DamageSource damageSource) {
        final float attackKnockback = (float) attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK);

        return EnchantmentHelper.modifyKnockback(ServerLevel.class.cast(attacker.level()), attacker.getWeaponItem(), target, damageSource, attackKnockback) / 2.0D;
    }

    private boolean isAttackKnockback(final CustomDamageEvent damageEvent) {
        if (damageEvent.getCause() != DamageCause.ENTITY_ATTACK || damageEvent.getProjectile() != null) {
            return false;
        }

        if (!CraftLivingEntity.class.isInstance(damageEvent.getDamager()) || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee())) {
            return false;
        }

        if (HoglinBase.class.isInstance(CraftLivingEntity.class.cast(damageEvent.getDamager()).getHandle())) {
            return false;
        }

        if (!CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return false;
        }

        return true;
    }
}