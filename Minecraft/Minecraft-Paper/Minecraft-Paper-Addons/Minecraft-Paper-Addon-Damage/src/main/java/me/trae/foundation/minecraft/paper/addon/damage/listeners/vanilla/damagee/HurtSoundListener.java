package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.stash.SoundStash;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@AddonSingleton
public final class HurtSoundListener implements Listener {

    @EventHandler
    public void onDamageApplied(final DamageAppliedEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !damageEvent.isApplied() || !damageEvent.isFullDamage()) {
            return;
        }

        final SoundStash soundStash = damageEvent.getSoundStash();

        if (soundStash == null) {
            return;
        }

        soundStash.play(damageEvent.getDamagee().getLocation());
    }
}