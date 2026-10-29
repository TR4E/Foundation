package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPotionEffectEvent.Cause;

@AddonSingleton
public final class MobAttackEffectsListener implements Listener {

    private static final int POISON_SECONDS_NORMAL = 7;
    private static final int POISON_SECONDS_HARD = 15;
    private static final int WITHER_DURATION = 200;
    private static final int HUNGER_DURATION = 140;

    private static final float IGNITE_CHANCE = 0.3F;

    @EventHandler
    public void onDamageApplied(final DamageAppliedEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || damageEvent.getBlockedDamage() > 0.0D || damageEvent.getCause() != DamageCause.ENTITY_ATTACK || damageEvent.getProjectile() != null) {
            return;
        }

        if (!CraftLivingEntity.class.isInstance(damageEvent.getDamager()) || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee())) {
            return;
        }

        final LivingEntity attacker = CraftLivingEntity.class.cast(damageEvent.getDamager()).getHandle();

        final LivingEntity target = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        if (CaveSpider.class.isInstance(attacker)) {
            this.resolveCaveSpider(attacker, target);
        }

        if (WitherSkeleton.class.isInstance(attacker)) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_DURATION), attacker, Cause.ATTACK);
        }

        if (Husk.class.isInstance(attacker)) {
            this.resolveHusk(attacker, target);
        }

        if (Zombie.class.isInstance(attacker)) {
            this.resolveZombie(attacker, target);
        }
    }

    private void resolveCaveSpider(final LivingEntity attacker, final LivingEntity target) {
        final Difficulty difficulty = attacker.level().getDifficulty();

        final int seconds = difficulty == Difficulty.NORMAL ? POISON_SECONDS_NORMAL : difficulty == Difficulty.HARD ? POISON_SECONDS_HARD : 0;

        if (seconds <= 0) {
            return;
        }

        target.addEffect(new MobEffectInstance(MobEffects.POISON, seconds * 20, 0), attacker, Cause.ATTACK);
    }

    private void resolveHusk(final LivingEntity attacker, final LivingEntity target) {
        if (!attacker.getMainHandItem().isEmpty()) {
            return;
        }

        target.addEffect(new MobEffectInstance(MobEffects.HUNGER, HUNGER_DURATION * (int) this.getEffectiveDifficulty(attacker)), attacker, Cause.ATTACK);
    }

    private void resolveZombie(final LivingEntity attacker, final LivingEntity target) {
        final float effectiveDifficulty = this.getEffectiveDifficulty(attacker);

        if (!attacker.getMainHandItem().isEmpty() || !attacker.isOnFire() || attacker.getRandom().nextFloat() >= effectiveDifficulty * IGNITE_CHANCE) {
            return;
        }

        final EntityCombustByEntityEvent combustEvent = new EntityCombustByEntityEvent(attacker.getBukkitEntity(), target.getBukkitEntity(), (float) (2 * (int) effectiveDifficulty));

        if (!combustEvent.callEvent()) {
            return;
        }

        target.igniteForSeconds(combustEvent.getDuration(), false);
    }

    private float getEffectiveDifficulty(final LivingEntity attacker) {
        return ServerLevel.class.cast(attacker.level()).getCurrentDifficultyAt(attacker.blockPosition()).getEffectiveDifficulty();
    }
}