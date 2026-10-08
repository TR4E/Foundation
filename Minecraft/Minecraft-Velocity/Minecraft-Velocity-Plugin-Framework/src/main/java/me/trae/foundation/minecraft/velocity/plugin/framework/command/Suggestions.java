package me.trae.foundation.minecraft.velocity.plugin.framework.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.experimental.UtilityClass;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.registry.PluginRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

@UtilityClass
public class Suggestions {

    public static <Type> List<String> filter(final Collection<? extends Type> collection, final Predicate<? super Type> predicate, final Function<? super Type, String> function, final String arg) {
        final String input = arg.toLowerCase(Locale.ROOT);

        return collection.stream()
                .filter(predicate)
                .map(function)
                .filter(string -> string.toLowerCase(Locale.ROOT).startsWith(input))
                .toList();
    }

    public static <Type> List<String> filter(final Collection<? extends Type> collection, final Function<? super Type, String> function, final String arg) {
        return filter(collection, type -> true, function, arg);
    }

    public static List<String> filter(final Collection<String> collection, final String arg) {
        return filter(collection, Function.identity(), arg);
    }

    public static List<String> childCommands(final BaseCommand<?, ?> baseCommand, final CommandSource commandSource, final String arg) {
        return filter(
                baseCommand.getChildCommandMap().values(),
                childCommand -> childCommand.isValidSender(commandSource, false) && childCommand.hasPermission(commandSource, false),
                BaseCommand::getLabel,
                arg
        );
    }

    public static List<String> players(final Predicate<Player> predicate, final String arg) {
        return filter(
                Injector.INSTANCE.get(ProxyServer.class).getAllPlayers(),
                predicate,
                Player::getUsername,
                arg
        );
    }

    public static List<String> players(final String arg) {
        return filter(
                Injector.INSTANCE.get(ProxyServer.class).getAllPlayers(),
                Player::getUsername,
                arg
        );
    }

    public static List<String> internalPlugins(final String arg) {
        return filter(
                PluginRegistry.getInternalPlugins(),
                velocityPlugin -> velocityPlugin.getClass().getSimpleName(),
                arg
        );
    }
}