package me.trae.foundation.minecraft.paper.addon.npc.snapshot;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.MobGoals;
import me.trae.foundation.minecraft.paper.addon.npc.goals.RestorableGoal;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.HashMap;
import java.util.Map;

public final class NpcGoalSnapshot {

    private final Map<GoalKey<Mob>, Goal<Mob>> goalMap = new HashMap<>();

    NpcGoalSnapshot(final LivingEntity livingEntity) {
        if (!(livingEntity instanceof final Mob mob)) {
            return;
        }

        for (final Goal<Mob> goal : Bukkit.getServer().getMobGoals().getAllGoals(mob)) {
            if (!(goal instanceof RestorableGoal)) {
                continue;
            }

            this.goalMap.put(goal.getKey(), goal);
        }
    }

    @SuppressWarnings("unchecked")
    public void apply(final LivingEntity livingEntity) {
        if (!(livingEntity instanceof final Mob mob)) {
            return;
        }

        final MobGoals mobGoals = Bukkit.getServer().getMobGoals();

        this.goalMap.forEach((goalKey, goal) -> {
            final Goal<Mob> currentGoal = mobGoals.getGoal(mob, goalKey);
            if (!(currentGoal instanceof RestorableGoal) || !currentGoal.getClass().equals(goal.getClass())) {
                return;
            }

            ((RestorableGoal<Goal<Mob>>) currentGoal).restore(goal);
        });
    }
}