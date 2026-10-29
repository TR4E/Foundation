package me.trae.foundation.minecraft.paper.addon.death.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.death.creators.DeathCreator;
import me.trae.foundation.minecraft.paper.addon.death.events.CustomDeathMessageEvent;
import me.trae.foundation.minecraft.paper.addon.death.events.DeathEvent;
import me.trae.foundation.minecraft.paper.addon.death.events.VanillaDeathEvent;
import me.trae.foundation.minecraft.paper.addon.death.services.DeathService;
import me.trae.foundation.minecraft.paper.addon.death.settings.DeathAddonSettings;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.stash.SoundStash;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import org.bukkit.entity.LivingEntity;
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

    private final DeathService deathService;
    private final List<DeathCreator> deathCreatorList;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(final EntityDeathEvent entityDeathEvent) {
        if (entityDeathEvent.isCancelled()) {
            return;
        }

        final DeathAddonSettings settings = this.deathService.getSettings();

        final Event event = Event.class.cast(this.deathCreatorList.isEmpty() ? new VanillaDeathEvent(entityDeathEvent, settings) : this.deathCreatorList.getFirst().createDeathEvent(entityDeathEvent, settings));
        if (event == null) {
            return;
        }

        final DeathEvent deathEvent = DeathEvent.class.cast(UtilEvent.supply(event));

        entityDeathEvent.getDrops().clear();
        entityDeathEvent.getDrops().addAll(deathEvent.getDrops());

        entityDeathEvent.setDroppedExp(deathEvent.getDropExp());

        final LivingEntity entity = entityDeathEvent.getEntity();

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
                    soundStash.play(entity.getLocation());
                });
            } else {
                entityDeathEvent.setShouldPlayDeathSound(false);
            }
        }

        if (settings.showDeathMessagesFor(entity)) {
            for (final Player recipient : UtilServer.getOnlinePlayers()) {
                UtilEvent.dispatch(new CustomDeathMessageEvent(deathEvent, recipient));
            }
        }
    }
}