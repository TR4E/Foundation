package me.trae.foundation.minecraft.paper.addon.death.listeners;

import me.trae.foundation.minecraft.paper.addon.death.events.CustomDeathMessageEvent;
import me.trae.foundation.minecraft.paper.addon.death.events.DeathEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.displayname.DisplayName;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilMessage;
import me.trae.foundation.utilities.UtilString;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

@AddonSingleton
public final class DeathMessageListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        event.deathMessage(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCustomDeathMessage(final CustomDeathMessageEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final DeathEvent deathEvent = event.getDeathEvent();

        final Player recipient = event.getRecipient();

        final Component entityName = event.getEntityName().getFullComponent();

        final Entity killer = deathEvent.getKiller();

        if (deathEvent.getCause() == EntityDamageEvent.DamageCause.SUICIDE || deathEvent.getCause() == EntityDamageEvent.DamageCause.KILL) {
            UtilMessage.message(recipient, "Death", "%s was killed.".formatted(UtilMessage.serializeWithReset(entityName)));
            return;
        }

        if (killer != null) {
            final String formattedKillerName = this.getFormattedKillerName(killer, event.getKillerName());

            if (deathEvent.getReasonName() == null) {
                UtilMessage.message(recipient, "Death", "%s was killed by %s.".formatted(UtilMessage.serializeWithReset(entityName), formattedKillerName));
            } else {
                UtilMessage.message(recipient, "Death", "%s was killed by %s with %s.".formatted(UtilMessage.serializeWithReset(entityName), formattedKillerName, this.getFormattedReasonName(deathEvent)));
            }
            return;
        }

        UtilMessage.message(recipient, "Death", "%s was killed by %s.".formatted(UtilMessage.serializeWithReset(entityName), UtilMessage.serializeWithReset(deathEvent.getCauseName())));
    }

    private String getFormattedKillerName(final Entity killer, final DisplayName killerName) {
        if (killerName.getName() == null) {
            return null;
        }

        String name = UtilMessage.serializeWithReset(killerName.getFullComponent());
        if (!(killer instanceof Player)) {
            name = UtilString.getIndefiniteArticlePrefix(killer.getName()) + name;
        }

        return UtilMessage.serializeWithReset(UtilMessage.deserialize(name));
    }

    private String getFormattedReasonName(final DeathEvent deathEvent) {
        final Component reasonName = deathEvent.getReasonName();
        if (reasonName == null) {
            return null;
        }

        final String indefiniteArticlePrefix = deathEvent.isReasonNameWithIndefiniteArticlePrefix() ? "" : UtilString.getIndefiniteArticlePrefix(PlainTextComponentSerializer.plainText().serialize(reasonName));

        return indefiniteArticlePrefix + UtilMessage.serializeWithReset(reasonName);
    }
}