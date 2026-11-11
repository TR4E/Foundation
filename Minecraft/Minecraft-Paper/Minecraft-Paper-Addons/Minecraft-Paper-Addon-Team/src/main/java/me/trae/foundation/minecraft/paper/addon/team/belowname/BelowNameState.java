package me.trae.foundation.minecraft.paper.addon.team.belowname;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.scoreboard.Objective;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
@Getter
public final class BelowNameState {

    private final ConcurrentHashMap<UUID, BelowName> belowNameMap = new ConcurrentHashMap<>();

    private final Objective objective;
}