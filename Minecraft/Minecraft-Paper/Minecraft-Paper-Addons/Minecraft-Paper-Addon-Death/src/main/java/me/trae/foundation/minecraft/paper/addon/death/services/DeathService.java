package me.trae.foundation.minecraft.paper.addon.death.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.death.settings.DeathAddonSettings;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;

@AllArgsConstructor
@Getter
@AddonSingleton
public final class DeathService {

    private final DeathAddonSettings settings;
}