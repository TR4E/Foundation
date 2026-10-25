package me.trae.foundation.minecraft.paper.addon.npc.types;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public interface LookingNpc {

    default Class<? extends LivingEntity> getLookingAtType() {
        return Player.class;
    }

    default double getLookingDistance() {
        return 8.0D;
    }

    default float getLookingProbability() {
        return 0.02F;
    }

    default boolean isLookingOnlyHorizontal() {
        return false;
    }
}