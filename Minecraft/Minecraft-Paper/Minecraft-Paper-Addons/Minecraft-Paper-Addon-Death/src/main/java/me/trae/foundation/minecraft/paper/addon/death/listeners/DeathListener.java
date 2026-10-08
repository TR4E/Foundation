package me.trae.foundation.minecraft.paper.addon.death.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.death.creators.DeathCreator;
import me.trae.foundation.minecraft.paper.addon.death.events.CustomDeathMessageEvent;
import me.trae.foundation.minecraft.paper.addon.death.events.DeathEvent;
import me.trae.foundation.minecraft.paper.addon.death.events.VanillaDeathEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.stash.SoundStash;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;

@AllArgsConstructor
@AddonSingleton
public final class DeathListener implements Listener {

    private final List<DeathCreator> deathCreatorList;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(final EntityDeathEvent entityDeathEvent) {
        if (entityDeathEvent.isCancelled()) {
            return;
        }

        final Event supplied = UtilEvent.supply(Event.class.cast(this.deathCreatorList.isEmpty() ? new VanillaDeathEvent(entityDeathEvent) : this.deathCreatorList.getFirst().createDeathEvent(entityDeathEvent)));

        if (!(supplied instanceof final DeathEvent deathEvent)) {
            return;
        }

        entityDeathEvent.getDrops().clear();
        entityDeathEvent.getDrops().addAll(deathEvent.getDrops());

        entityDeathEvent.setDroppedExp(deathEvent.getDropExp());

        if (entityDeathEvent.shouldPlayDeathSound()) {
            final SoundStash soundStash = deathEvent.getSoundStash();
            if (soundStash != null) {
                soundStash.getSound().ifPresentOrElse(sound -> {
                    entityDeathEvent.setDeathSound(sound);
                    entityDeathEvent.setDeathSoundCategory(soundStash.getCategory());
                    entityDeathEvent.setDeathSoundVolume(soundStash.getVolume());
                    entityDeathEvent.setDeathSoundPitch(soundStash.getPitch());
                }, () -> {
                    entityDeathEvent.setShouldPlayDeathSound(false);
                    soundStash.play(entityDeathEvent.getEntity().getLocation());
                });
            } else {
                entityDeathEvent.setShouldPlayDeathSound(false);
            }
        }

        for (final Player recipient : UtilServer.getOnlinePlayers()) {
            UtilEvent.dispatch(new CustomDeathMessageEvent(deathEvent, recipient));
        }
    }
}