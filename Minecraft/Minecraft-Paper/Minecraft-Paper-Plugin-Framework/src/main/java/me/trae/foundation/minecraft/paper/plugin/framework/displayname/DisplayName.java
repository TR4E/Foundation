package me.trae.foundation.minecraft.paper.plugin.framework.displayname;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.common.adventure.UtilAdventure;
import net.kyori.adventure.text.Component;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class DisplayName {

    private final Component prefix, name, suffix;

    public static DisplayName of(final Component prefix, final Component name, final Component suffix) {
        return new DisplayName(prefix, name, suffix);
    }

    public static DisplayName of(final Component name) {
        return of(null, name, null);
    }

    public final Component getFullComponent() {
        return UtilAdventure.joinWithEmpty(this.prefix, this.name, this.suffix);
    }
}