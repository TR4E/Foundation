package me.trae.foundation.minecraft.paper.addon.damage.settings;

import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.damage.enums.DelayMode;

public final class DamageSettings {

    @Getter
    @Setter
    private static DelayMode delayMode = DelayMode.MODERN;
}