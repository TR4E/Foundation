package me.trae.foundation.minecraft.paper.addon.damage.enums;

public enum DelayMode {

    /**
     * Uses the target's no-damage ticks. During the protected window, equal or weaker hits are rejected
     * and stronger hits apply only the damage increase, unless the damage source bypasses the cooldown.
     */
    MODERN,

    /**
     * Uses separate millisecond cooldowns per target and attacker, or per target and damage cause when
     * no attacker exists. Hits during an active cooldown are rejected; accepted hits apply full damage.
     */
    LEGACY
}