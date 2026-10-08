package me.trae.foundation.minecraft.common.adventure;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

@UtilityClass
public class UtilAdventure {

    public static Component join(final ComponentLike separator, final Predicate<ComponentLike> predicate, final Component... components) {
        final JoinConfiguration.Builder builder = JoinConfiguration.builder();

        if (separator != null) {
            builder.separator(separator);
        }

        if (predicate != null) {
            builder.predicate(predicate);
        }

        return Component.join(builder.build(), Arrays.stream(components).filter(Objects::nonNull).toArray(Component[]::new));
    }

    public static Component join(final ComponentLike separator, final Component... components) {
        return join(separator, null, components);
    }

    public static Component joinWithSpace(final Component... components) {
        return join(Component.space(), componentLike -> componentLike != Component.empty(), components);
    }

    public static Component joinWithEmpty(final Component... components) {
        return join(Component.empty(), null, components);
    }

    public static Component joinWithNewLine(final Component... components) {
        return join(Component.newline(), componentLike -> componentLike != Component.empty(), components);
    }
}