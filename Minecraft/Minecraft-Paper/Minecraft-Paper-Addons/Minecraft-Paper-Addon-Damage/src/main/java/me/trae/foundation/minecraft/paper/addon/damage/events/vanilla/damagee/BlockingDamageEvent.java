package me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damagee;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;

@AllArgsConstructor
@Getter
@Setter
public final class BlockingDamageEvent extends CustomCancellableEvent {

    private final CustomDamageEvent damageEvent;

    private double blockedDamage;
}