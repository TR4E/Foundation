package me.trae.foundation.minecraft.paper.addon.npc.types;

import java.time.temporal.ChronoUnit;

public interface RespawnableNpc {

    default int getRespawnDelay() {
        return 5;
    }

    default ChronoUnit getRespawnInterval() {
        return ChronoUnit.SECONDS;
    }
}