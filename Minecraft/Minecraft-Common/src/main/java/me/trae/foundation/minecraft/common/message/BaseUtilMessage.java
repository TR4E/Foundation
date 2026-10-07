package me.trae.foundation.minecraft.common.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public abstract class BaseUtilMessage {

    public static String serialize(final Component component) {
        return Message.getMiniMessage().serialize(component);
    }

    public static String serializeWithReset(final Component component) {
        return Message.getMiniMessage().serialize(component) + "<reset>";
    }

    public static Component deserialize(final String string) {
        return Message.getMiniMessage().deserialize(string);
    }

    public static void message(final Audience audience, final Component message) {
        audience.sendMessage(Message.render(null, message));
    }

    public static void message(final Audience audience, final String message) {
        audience.sendMessage(Message.render(null, message));
    }

    public static void message(final Audience audience, final Component prefix, final Component message) {
        audience.sendMessage(Message.render(prefix, message));
    }

    public static void message(final Audience audience, final Component prefix, final String message) {
        audience.sendMessage(Message.render(prefix, Message.getMiniMessage().deserialize(message)));
    }

    public static void message(final Audience audience, final String prefix, final String message) {
        audience.sendMessage(Message.render(prefix, message));
    }

    public static void message(final Audience audience, final String prefix, final Component message) {
        audience.sendMessage(Message.render(Message.prefix(prefix), message));
    }

    public static void message(final Collection<? extends Audience> audiences, final Component prefix, final Component message, final Collection<UUID> ignored) {
        message(filter(audiences, ignored), prefix, message);
    }

    public static void message(final Collection<? extends Audience> audiences, final Component prefix, final String message, final Collection<UUID> ignored) {
        message(filter(audiences, ignored), prefix, message);
    }

    public static void message(final Collection<? extends Audience> audiences, final String prefix, final String message, final Collection<UUID> ignored) {
        message(filter(audiences, ignored), prefix, message);
    }

    public static void message(final Collection<? extends Audience> audiences, final String prefix, final Component message, final Collection<UUID> ignored) {
        message(filter(audiences, ignored), prefix, message);
    }

    protected static Audience filter(final Collection<? extends Audience> audiences, final Collection<UUID> ignored) {
        final Audience audience = Audience.audience(audiences);

        if (ignored == null || ignored.isEmpty()) {
            return audience;
        }

        final Set<UUID> ignoredSet = Set.copyOf(ignored);

        return audience.filterAudience(target -> target.get(Identity.UUID).map(id -> !ignoredSet.contains(id)).orElse(true));
    }
}