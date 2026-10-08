package me.trae.foundation.minecraft.paper.plugin.framework.command;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.registry.PluginRegistry;
import me.trae.foundation.utilities.UtilString;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

@UtilityClass
public class Suggestions {

    private static final List<String> MATERIAL_NAME_LIST = Stream.of(Material.values()).map(material -> UtilString.clean(material.name()).replace(" ", "_")).toList();

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

    public static List<String> childCommands(final BaseCommand<?, ?> baseCommand, final CommandSender commandSender, final String arg) {
        return filter(
                baseCommand.getChildCommandMap().values(),
                childCommand -> childCommand.isValidSender(commandSender, false) && childCommand.hasPermission(commandSender, false),
                BaseCommand::getLabel,
                arg
        );
    }

    public static List<String> players(final Predicate<Player> predicate, final String arg) {
        return filter(
                Bukkit.getServer().getOnlinePlayers(),
                predicate,
                Player::getName,
                arg
        );
    }

    public static List<String> players(final String arg) {
        return filter(
                Bukkit.getServer().getOnlinePlayers(),
                Player::getName,
                arg
        );
    }

    public static List<String> worlds(final String arg) {
        return filter(
                Bukkit.getServer().getWorlds(),
                WorldInfo::getName,
                arg
        );
    }

    public static List<String> materials(final String arg) {
        return filter(
                MATERIAL_NAME_LIST,
                arg
        );
    }

    public static List<String> internalPlugins(final String arg) {
        return filter(
                PluginRegistry.getInternalPlugins(),
                Plugin::getName,
                arg
        );
    }

    public static String positionX(final CommandSender commandSender) {
        return commandSender instanceof final Player player ? String.valueOf(player.getLocation().getX()) : "";
    }

    public static String positionY(final CommandSender commandSender) {
        return commandSender instanceof final Player player ? String.valueOf(player.getLocation().getY()) : "";
    }

    public static String positionZ(final CommandSender commandSender) {
        return commandSender instanceof final Player player ? String.valueOf(player.getLocation().getZ()) : "";
    }
}