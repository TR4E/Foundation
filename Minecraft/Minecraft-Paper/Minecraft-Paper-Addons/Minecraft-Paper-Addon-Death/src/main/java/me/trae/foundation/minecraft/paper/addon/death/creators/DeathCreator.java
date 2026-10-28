package me.trae.foundation.minecraft.paper.addon.death.creators;

import me.trae.foundation.minecraft.paper.addon.death.events.DeathEvent;
import me.trae.foundation.minecraft.paper.addon.death.settings.DeathAddonSettings;
import org.bukkit.event.entity.EntityDeathEvent;

public interface DeathCreator {

    DeathEvent createDeathEvent(final EntityDeathEvent entityDeathEvent, final DeathAddonSettings deathAddonSettings);
}