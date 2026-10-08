package me.trae.foundation.minecraft.paper.addon.death.creators;

import me.trae.foundation.minecraft.paper.addon.death.events.DeathEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public interface DeathCreator {

    DeathEvent createDeathEvent(final EntityDeathEvent entityDeathEvent);
}