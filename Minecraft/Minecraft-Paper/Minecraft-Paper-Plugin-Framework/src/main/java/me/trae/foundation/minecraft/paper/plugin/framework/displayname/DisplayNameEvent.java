package me.trae.foundation.minecraft.paper.plugin.framework.displayname;

import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@Getter
@Setter
public class DisplayNameEvent extends CustomEvent {

    private final Entity entity;
    private final Player recipient;

    private DisplayName displayName;

    public DisplayNameEvent(final Entity entity, final Player recipient) {
        this.entity = entity;
        this.recipient = recipient;

        this.displayName = DisplayName.of(Component.text(entity.getName(), NamedTextColor.YELLOW));
    }
}