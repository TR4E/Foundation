package me.trae.foundation.minecraft.paper.addon.damage.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;

@AllArgsConstructor
@Getter
@Setter
public final class DamageKnockbackEvent extends CustomCancellableEvent {

    private final CustomDamageEvent damageEvent;

    private double directionX, directionZ, strength;
    private boolean sprintReset;
}