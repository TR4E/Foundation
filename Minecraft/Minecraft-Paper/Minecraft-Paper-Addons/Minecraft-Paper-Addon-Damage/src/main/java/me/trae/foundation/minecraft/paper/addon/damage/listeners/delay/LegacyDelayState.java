package me.trae.foundation.minecraft.paper.addon.damage.listeners.delay;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.UUID;

@RequiredArgsConstructor
@Getter
@Setter
public final class LegacyDelayState {

    private final UUID damageeId, damagerId;
    private final DamageCause cause;
    private final long systemTime;

    private long durationMillis;
}