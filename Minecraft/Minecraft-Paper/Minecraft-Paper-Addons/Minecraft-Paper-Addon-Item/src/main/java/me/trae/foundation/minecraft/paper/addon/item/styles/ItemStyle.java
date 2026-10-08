package me.trae.foundation.minecraft.paper.addon.item.styles;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;

import java.awt.Color;
import java.util.List;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class ItemStyle {

    private final String name;

    private final Color color;

    private final List<TextDecoration> decorations;

    private final NamespacedKey tooltipStyle;

    private final String tag;

    public static ItemStyle of(final String name, final Color color, final List<TextDecoration> decorations, final NamespacedKey tooltipStyle, final String tag) {
        return new ItemStyle(name, color, decorations, tooltipStyle, tag);
    }

    public static ItemStyle of(final String name, final Color color, final NamespacedKey tooltipStyle, final String tag) {
        return of(name, color, null, tooltipStyle, tag);
    }
}