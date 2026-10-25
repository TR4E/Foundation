package me.trae.foundation.minecraft.paper.addon.npc.types;

import org.bukkit.Location;

import java.util.Collections;
import java.util.List;

public interface MovableNpc {

    default boolean hasGravity() {
        return true;
    }

    default List<Location> getPathLocations() {
        return Collections.emptyList();
    }

    default boolean isPathLooping() {
        return true;
    }

    default long getPathWaitTicks() {
        return 0L;
    }

    default double getMovementSpeed() {
        return 1.0D;
    }
}