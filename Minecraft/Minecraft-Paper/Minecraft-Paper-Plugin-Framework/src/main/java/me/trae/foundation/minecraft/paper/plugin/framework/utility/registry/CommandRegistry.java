package me.trae.foundation.minecraft.paper.plugin.framework.utility.registry;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.paper.plugin.framework.command.AbstractCommand;
import me.trae.foundation.minecraft.paper.plugin.framework.command.wrapper.BukkitCommandWrapper;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;

@UtilityClass
public class CommandRegistry {

    public static void registerCommand(final Plugin plugin, final AbstractCommand<?, ?> abstractCommand) {
        if (!abstractCommand.isRoot()) {
            final LinkedHashMap<String, AbstractCommand<?, ?>> childCommandMap = abstractCommand.getParentCommand().getChildCommandMap();

            childCommandMap.put(abstractCommand.getLabel(), abstractCommand);

            abstractCommand.getAliases().forEach(alias -> childCommandMap.put(alias, abstractCommand));

            abstractCommand.getShortcuts().forEach(shortcut -> registerCommand(plugin, new BukkitCommandWrapper(abstractCommand, shortcut)));
            return;
        }

        registerCommand(plugin, new BukkitCommandWrapper(abstractCommand));
    }

    private static void registerCommand(final Plugin plugin, final BukkitCommandWrapper bukkitCommandWrapper) {
        final CommandMap commandMap = Bukkit.getServer().getCommandMap();

        removeCommand(commandMap, bukkitCommandWrapper.getLabel());

        bukkitCommandWrapper.getAliases().forEach(alias -> removeCommand(commandMap, alias));

        commandMap.register(bukkitCommandWrapper.getLabel(), plugin.getName(), bukkitCommandWrapper);
    }

    public static void unregisterCommand(final AbstractCommand<?, ?> abstractCommand) {
        if (!abstractCommand.isRoot()) {
            final LinkedHashMap<String, AbstractCommand<?, ?>> childCommandMap = abstractCommand.getParentCommand().getChildCommandMap();

            childCommandMap.remove(abstractCommand.getLabel(), abstractCommand);

            abstractCommand.getAliases().forEach(alias -> childCommandMap.remove(alias, abstractCommand));
        }

        final CommandMap commandMap = Bukkit.getServer().getCommandMap();

        commandMap.getKnownCommands().values().stream()
                .filter(command -> command instanceof final BukkitCommandWrapper bukkitCommandWrapper && bukkitCommandWrapper.getAbstractCommand() == abstractCommand)
                .distinct()
                .toList()
                .forEach(command -> removeCommand(commandMap, command));
    }

    private static void removeCommand(final CommandMap commandMap, final String label) {
        final Command command = commandMap.getCommand(label);
        if (command == null) {
            return;
        }

        removeCommand(commandMap, command);
    }

    private static void removeCommand(final CommandMap commandMap, final Command command) {
        commandMap.getKnownCommands().values().removeIf(value -> value == command);

        command.unregister(commandMap);
    }
}