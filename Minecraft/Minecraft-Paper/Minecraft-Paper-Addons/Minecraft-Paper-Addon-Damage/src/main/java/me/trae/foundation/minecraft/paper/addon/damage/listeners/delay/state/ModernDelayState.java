package me.trae.foundation.minecraft.paper.addon.damage.listeners.delay.state;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@Getter
@Setter
public final class ModernDelayState {

    private final float lastDamage;
    private final int previousDuration, remainingTicks;

    private float incomingDamage;
    private int durationTicks;
    private boolean prepared;
}