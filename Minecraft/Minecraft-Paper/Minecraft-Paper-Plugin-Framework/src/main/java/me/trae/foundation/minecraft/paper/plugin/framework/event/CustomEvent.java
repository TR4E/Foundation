package me.trae.foundation.minecraft.paper.plugin.framework.event;

import lombok.NoArgsConstructor;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor
public class CustomEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    public CustomEvent(final boolean isAsync) {
        super(isAsync);
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }
}