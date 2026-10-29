package me.trae.foundation.minecraft.paper.addon.damage.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.death.reason.Reason;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.stash.SoundStash;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@Getter
@Setter
public final class CustomDamageEvent extends CustomCancellableEvent {

    private final long systemTime = System.currentTimeMillis();

    private final Entity damagee, damager;
    private final Projectile projectile;
    private final DamageSource source;
    private final DamageCause cause;
    private final ItemStack itemStack;
    private final ItemStack[] armourContents;
    private final double originalDamage;

    private double damage, blockedDamage, resistedDamage, absorbedDamage;
    private boolean critical, fullDamage, applied;

    private SoundStash soundStash;
    private Reason reason;

    // EntityDamageEvent
    public CustomDamageEvent(final EntityDamageEvent entityDamageEvent) {
        final Entity entity = entityDamageEvent.getEntity();

        final double damage = entityDamageEvent.getDamage();

        this(
                entity,
                null,
                null,
                getDamageSource(entityDamageEvent.getDamageSource()),
                entityDamageEvent.getCause(),
                null,
                UtilItemStack.getArmourContents(entity),
                damage,
                damage,
                0.0D,
                0.0D,
                0.0D,
                false,
                false,
                false,
                getHurtSound(entity),
                null
        );
    }

    // EntityDamageByEntityEvent
    public CustomDamageEvent(final EntityDamageByEntityEvent entityDamageByEntityEvent) {
        final Entity entity = entityDamageByEntityEvent.getEntity();

        final double damage = entityDamageByEntityEvent.getDamage();

        Entity damager = entityDamageByEntityEvent.getDamager();
        Projectile projectile = null;

        if (damager instanceof final Projectile projectileDamager) {
            projectile = projectileDamager;

            if (projectileDamager.getShooter() instanceof final Entity shooter) {
                damager = shooter;
            }
        }

        this(
                entity,
                damager,
                projectile,
                getDamageSource(entityDamageByEntityEvent.getDamageSource()),
                entityDamageByEntityEvent.getCause(),
                UtilItemStack.getItemInMainHand(damager).orElse(null),
                UtilItemStack.getArmourContents(entity),
                damage,
                damage,
                0.0D,
                0.0D,
                0.0D,
                entityDamageByEntityEvent.isCritical(),
                false,
                false,
                getHurtSound(entity),
                null
        );
    }

    // Custom (with Damager)
    public CustomDamageEvent(final Entity damagee, final Entity damager, final DamageSource source, final DamageCause cause, final double damage) {
        this(
                damagee,
                damager,
                null,
                getDamageSource(source),
                cause,
                null,
                UtilItemStack.getArmourContents(damagee),
                damage,
                damage,
                0.0D,
                0.0D,
                0.0D,
                false,
                false,
                false,
                getHurtSound(damagee),
                null
        );
    }

    // Custom (without Damager)
    public CustomDamageEvent(final Entity damagee, final DamageSource source, final DamageCause cause, final double damage) {
        this(
                damagee,
                null,
                null,
                getDamageSource(source),
                cause,
                null,
                UtilItemStack.getArmourContents(damagee),
                damage,
                damage,
                0.0D,
                0.0D,
                0.0D,
                false,
                false,
                false,
                getHurtSound(damagee),
                null
        );
    }

    public void addDamage(final double damage) {
        this.damage += damage;
    }

    public void subtractDamage(final double damage) {
        this.damage -= damage;
    }

    public void multiplyDamage(final double multiplier) {
        this.damage *= multiplier;
    }

    public double getFinalDamage() {
        return Math.max(0.0D, this.damage);
    }

    public boolean hasDamage() {
        return this.getFinalDamage() > 0.0D;
    }

    public boolean isMelee() {
        return this.cause == DamageCause.ENTITY_ATTACK || this.cause == DamageCause.ENTITY_SWEEP_ATTACK;
    }

    private static DamageSource getDamageSource(final DamageSource source) {
        return source != null ? source : DamageSource.builder(DamageType.GENERIC).build();
    }

    private static SoundStash getHurtSound(final Entity entity) {
        if (!(entity instanceof final CraftLivingEntity craftLivingEntity)) {
            return null;
        }

        final net.minecraft.world.entity.LivingEntity handle = craftLivingEntity.getHandle();

        final Sound sound = craftLivingEntity.getHurtSound();

        if (sound == null) {
            return null;
        }

        return SoundStash.of(sound, SoundCategory.valueOf(handle.getSoundSource().name()), handle.getSoundVolume(), handle.getVoicePitch());
    }
}