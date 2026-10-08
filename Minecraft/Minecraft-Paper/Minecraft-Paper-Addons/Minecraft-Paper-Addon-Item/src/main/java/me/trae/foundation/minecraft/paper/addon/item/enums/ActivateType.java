package me.trae.foundation.minecraft.paper.addon.item.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.event.block.Action;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Getter
public enum ActivateType {

    LEFT_CLICK("Left-Click", List.of(Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK)),
    RIGHT_CLICK("Right-Click", List.of(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK)),
    DROP_ITEM("Drop Item", Collections.emptyList()),
    SWAP_HAND("Swap-Hand", Collections.emptyList());

    private static final Map<Action, ActivateType> BY_ACTION_MAP = new HashMap<>();

    private final String name;

    private final List<Action> actions;

    static {
        for (final ActivateType activateType : values()) {
            for (final Action action : activateType.getActions()) {
                BY_ACTION_MAP.put(action, activateType);
            }
        }
    }

    public static Optional<ActivateType> getByAction(final Action action) {
        return Optional.ofNullable(BY_ACTION_MAP.get(action));
    }
}