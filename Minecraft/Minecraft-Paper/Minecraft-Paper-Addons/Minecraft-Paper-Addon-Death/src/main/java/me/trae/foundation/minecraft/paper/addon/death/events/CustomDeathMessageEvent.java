package me.trae.foundation.minecraft.paper.addon.death.events;

import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.plugin.framework.displayname.DisplayName;
import me.trae.foundation.minecraft.paper.plugin.framework.displayname.DisplayNameEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.entity.Player;

@Getter
@Setter
public final class CustomDeathMessageEvent extends CustomCancellableEvent {

    private final DeathEvent deathEvent;
    private final Player recipient;

    private DisplayName entityName, killerName;

    public CustomDeathMessageEvent(final DeathEvent deathEvent, final Player recipient) {
        this.deathEvent = deathEvent;
        this.recipient = recipient;

        this.entityName = UtilEvent.supply(new DisplayNameEvent(deathEvent.getEntity(), recipient)).getDisplayName();

        if (deathEvent.getKiller() != null) {
            this.killerName = UtilEvent.supply(new DisplayNameEvent(deathEvent.getKiller(), recipient)).getDisplayName();
        }
    }
}