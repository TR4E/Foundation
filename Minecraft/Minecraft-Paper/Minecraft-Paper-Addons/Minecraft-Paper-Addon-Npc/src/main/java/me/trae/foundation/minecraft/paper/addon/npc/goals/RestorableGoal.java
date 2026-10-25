package me.trae.foundation.minecraft.paper.addon.npc.goals;

import org.bukkit.entity.Mob;

public interface RestorableGoal<Goal extends com.destroystokyo.paper.entity.ai.Goal<Mob>> {

    void restore(final Goal goal);
}