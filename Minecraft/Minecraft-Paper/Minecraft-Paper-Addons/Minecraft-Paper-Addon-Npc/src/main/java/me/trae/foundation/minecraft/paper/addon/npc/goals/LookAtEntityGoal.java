package me.trae.foundation.minecraft.paper.addon.npc.goals;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import lombok.RequiredArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
public final class LookAtEntityGoal implements Goal<Mob> {

    private static final GoalKey<Mob> GOAL_KEY = GoalKey.of(Mob.class, new NamespacedKey("custom", "look_at_entity"));

    private final Class<? extends LivingEntity> targetClass;
    private final Mob mob;
    private final double distance;
    private final float probability;
    private final boolean onlyHorizontal;

    private LivingEntity target;
    private int ticks;

    @Override
    public boolean shouldActivate() {
        if (ThreadLocalRandom.current().nextFloat() >= this.probability) {
            return false;
        }

        this.target = this.getNearestTarget();

        return this.target != null;
    }

    @Override
    public boolean shouldStayActive() {
        if (this.ticks <= 0) {
            return false;
        }

        if (this.target == null || !this.target.isValid()) {
            return false;
        }

        if (!this.target.getWorld().equals(this.mob.getWorld())) {
            return false;
        }

        if (this.target.getLocation().distanceSquared(this.mob.getLocation()) > this.distance * this.distance) {
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        this.ticks = 40 + ThreadLocalRandom.current().nextInt(40);
    }

    @Override
    public void stop() {
        this.target = null;
        this.ticks = 0;
    }

    @Override
    public void tick() {
        final Location location = this.target.getEyeLocation();

        this.mob.lookAt(location.getX(), this.onlyHorizontal ? this.mob.getEyeLocation().getY() : location.getY(), location.getZ());

        this.ticks--;
    }

    @Override
    public GoalKey<Mob> getKey() {
        return GOAL_KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.LOOK);
    }

    private LivingEntity getNearestTarget() {
        final Location location = this.mob.getLocation();

        return this.mob.getWorld().getNearbyEntitiesByType(this.targetClass, location, this.distance)
                .stream()
                .filter(livingEntity -> !livingEntity.equals(this.mob))
                .filter(livingEntity -> !(livingEntity instanceof final Player player) || player.getGameMode() != GameMode.SPECTATOR)
                .min(Comparator.comparingDouble(livingEntity -> livingEntity.getLocation().distanceSquared(location)))
                .orElse(null);
    }
}