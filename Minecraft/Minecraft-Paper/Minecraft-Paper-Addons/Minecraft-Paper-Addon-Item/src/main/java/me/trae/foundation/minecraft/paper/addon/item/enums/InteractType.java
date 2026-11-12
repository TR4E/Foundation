package me.trae.foundation.minecraft.paper.addon.item.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.event.block.Action;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Getter
public enum InteractType {

    LEFT_CLICK("Left-Click", Action.LEFT_CLICK_BLOCK),
    RIGHT_CLICK("Right-Click", Action.RIGHT_CLICK_BLOCK);

    private final String name;
    private final Action action;

    private static final Map<Action, InteractType> BY_ACTION_MAP = new HashMap<>();

    static {
        for (final InteractType interactType : values()) {
            BY_ACTION_MAP.put(interactType.getAction(), interactType);
        }
    }

    public static Optional<InteractType> getByAction(final Action action) {
        return Optional.ofNullable(BY_ACTION_MAP.get(action));
    }
}