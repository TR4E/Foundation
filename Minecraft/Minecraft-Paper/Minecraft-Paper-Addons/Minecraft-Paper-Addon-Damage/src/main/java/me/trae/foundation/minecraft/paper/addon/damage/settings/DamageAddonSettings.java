package me.trae.foundation.minecraft.paper.addon.damage.settings;

import me.trae.foundation.minecraft.paper.addon.damage.enums.DeathMode;
import me.trae.foundation.minecraft.paper.addon.damage.enums.DelayMode;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.settings.AddonSettings;

public interface DamageAddonSettings extends AddonSettings {

    default long getLastPlayerDamagerDamageExpiration() {
        return 10_000L;
    }

    default DeathMode getDeathMode() {
        return DeathMode.NORMAL;
    }

    default DelayMode getDelayMode() {
        return DelayMode.MODERN;
    }
}