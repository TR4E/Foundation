package me.trae.foundation.minecraft.paper.addon.item.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.utilities.UtilString;

@AllArgsConstructor
@Getter
public enum BlockRemoveReason {

    EXPLOSION,
    BURN,
    FADE,
    DECAY,
    LIQUID,
    ENTITY,
    GROWTH;

    private final String name = UtilString.clean(this.name());
}