package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damager.AttackEffectsEvent;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackSnapshotListener.AttackSnapshot;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason;
import org.bukkit.inventory.ItemStack;

import java.util.WeakHashMap;

@AllArgsConstructor
@AddonSingleton
public final class AttackEffectsListener implements Listener {

    private static final float STRONG_STRENGTH = 0.9F;

    private static final int CRITICAL_PARTICLES = 12;

    private static final float STATISTIC_SCALE = 10.0F;

    private static final float INDICATOR_THRESHOLD = 2.0F;

    private final WeakHashMap<CustomDamageEvent, Double> healthMap = new WeakHashMap<>();

    private final AttackSnapshotListener attackSnapshotListener;

    @EventHandler
    public void onCustomDamage(final CustomDamageEvent event) {
        if (!this.isPlayerAttack(event) || !Damageable.class.isInstance(event.getDamagee())) {
            return;
        }

        this.healthMap.put(event, Damageable.class.cast(event.getDamagee()).getHealth());

        final AttackSnapshot attackSnapshot = this.attackSnapshotListener.getSnapshot(event);

        if (attackSnapshot == null || !attackSnapshot.sprinting() || attackSnapshot.strength() <= STRONG_STRENGTH) {
            return;
        }

        this.playSound(Player.class.cast(event.getDamager()), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.DELAY) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (!damageEvent.isCancelled() || !this.isPlayerAttack(damageEvent)) {
            return;
        }

        this.healthMap.remove(damageEvent);

        this.playSound(Player.class.cast(damageEvent.getDamager()), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE);
    }

    @EventHandler
    public void onDamageApplied(final DamageAppliedEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        final Double previousHealth = this.healthMap.remove(damageEvent);

        if (damageEvent.isCancelled() || !this.isPlayerAttack(damageEvent)) {
            return;
        }

        final Player player = Player.class.cast(damageEvent.getDamager());

        if (damageEvent.getBlockedDamage() > 0.0D) {
            this.playSound(player, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE);
            return;
        }

        final ServerPlayer attacker = CraftPlayer.class.cast(player).getHandle();

        final AttackSnapshot attackSnapshot = this.attackSnapshotListener.getSnapshot(damageEvent);

        final boolean sweeping = attackSnapshot != null && attackSnapshot.sweeping();

        final boolean strong = attackSnapshot != null && attackSnapshot.strength() > STRONG_STRENGTH;

        final Sound defaultSound = damageEvent.isCritical() ? Sound.ENTITY_PLAYER_ATTACK_CRIT : sweeping ? null : strong ? Sound.ENTITY_PLAYER_ATTACK_STRONG : Sound.ENTITY_PLAYER_ATTACK_WEAK;

        final AttackEffectsEvent effectsEvent = UtilEvent.supply(new AttackEffectsEvent(damageEvent, defaultSound, attacker.level().spigotConfig.combatExhaustion, damageEvent.isCritical() ? CRITICAL_PARTICLES : 0));

        if (effectsEvent.isCancelled()) {
            return;
        }

        if (effectsEvent.getSound() != null) {
            this.playSound(player, effectsEvent.getSound());
        }

        final Entity target = CraftEntity.class.cast(damageEvent.getDamagee()).getHandle();

        if (effectsEvent.getCriticalParticles() > 0) {
            attacker.crit(target);
        }

        if (this.hasEnchantmentBonus(damageEvent, attacker)) {
            attacker.magicCrit(target);
        }

        if (effectsEvent.getExhaustion() > 0.0F) {
            attacker.causeFoodExhaustion(effectsEvent.getExhaustion(), ExhaustionReason.ATTACK);
        }

        this.awardDamageDealt(attacker, target, previousHealth);
    }

    private void awardDamageDealt(final ServerPlayer attacker, final Entity target, final Double previousHealth) {
        if (previousHealth == null || !LivingEntity.class.isInstance(target)) {
            return;
        }

        final float damage = (float) (previousHealth - LivingEntity.class.cast(target).getHealth());

        final int statistic = Math.round(damage * STATISTIC_SCALE);

        if (statistic > 0) {
            attacker.awardStat(Stats.DAMAGE_DEALT, statistic);
        }

        if (damage <= INDICATOR_THRESHOLD) {
            return;
        }

        ServerLevel.class.cast(attacker.level()).sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.5D), target.getZ(), (int) (damage * 0.5D), 0.1D, 0.0D, 0.1D, 0.2D);
    }

    private void playSound(final Player player, final Sound sound) {
        player.getWorld().playSound(player.getLocation(), sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    private boolean isPlayerAttack(final CustomDamageEvent damageEvent) {
        if (damageEvent.getCause() != DamageCause.ENTITY_ATTACK) {
            return false;
        }

        if (damageEvent.getProjectile() != null) {
            return false;
        }

        if (!Player.class.isInstance(damageEvent.getDamager())) {
            return false;
        }

        return true;
    }

    private boolean hasEnchantmentBonus(final CustomDamageEvent damageEvent, final ServerPlayer attacker) {
        final ItemStack itemStack = damageEvent.getItemStack();

        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        if (!CraftLivingEntity.class.isInstance(damageEvent.getDamagee()) || !CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return false;
        }

        final float base = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);

        if (EnchantmentHelper.modifyDamage(ServerLevel.class.cast(attacker.level()), CraftItemStack.asNMSCopy(itemStack), CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle(), CraftDamageSource.class.cast(damageEvent.getSource()).getHandle(), base) <= base) {
            return false;
        }

        return true;
    }
}