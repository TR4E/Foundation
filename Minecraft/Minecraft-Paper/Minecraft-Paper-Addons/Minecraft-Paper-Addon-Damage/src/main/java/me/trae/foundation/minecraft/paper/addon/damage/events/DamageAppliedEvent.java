package me.trae.foundation.minecraft.paper.addon.damage.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;

@AllArgsConstructor
@Getter
public final class DamageAppliedEvent extends CustomEvent {

    private final CustomDamageEvent damageEvent;
}