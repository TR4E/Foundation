package me.trae.foundation.minecraft.paper.plugin.framework.utility.registry;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.paper.plugin.framework.command.BaseCommand;
import me.trae.foundation.minecraft.paper.plugin.framework.command.wrapper.BukkitCommandWrapper;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;

@UtilityClass
public class CommandRegistry {

    public static void registerCommand(final Plugin plugin, final BaseCommand<?, ?> baseCommand) {
        if (!baseCommand.isRoot()) {
            final LinkedHashMap<String, BaseCommand<?, ?>> childCommandMap = baseCommand.getParentCommand().getChildCommandMap();

            childCommandMap.put(baseCommand.getLabel(), baseCommand);

            baseCommand.getAliases().forEach(alias -> childCommandMap.put(alias, baseCommand));
            return;
        }

        final CommandMap commandMap = Bukkit.getServer().getCommandMap();

        removeCommand(commandMap, baseCommand.getLabel());

        baseCommand.getAliases().forEach(alias -> removeCommand(commandMap, alias));

        commandMap.register(baseCommand.getLabel(), plugin.getName(), new BukkitCommandWrapper(baseCommand));
    }

    public static void unregisterCommand(final BaseCommand<?, ?> baseCommand) {
        if (!baseCommand.isRoot()) {
            final LinkedHashMap<String, BaseCommand<?, ?>> childCommandMap = baseCommand.getParentCommand().getChildCommandMap();

            childCommandMap.remove(baseCommand.getLabel(), baseCommand);

            baseCommand.getAliases().forEach(alias -> childCommandMap.remove(alias, baseCommand));
            return;
        }

        removeCommand(Bukkit.getServer().getCommandMap(), baseCommand.getLabel());
    }

    private static void removeCommand(final CommandMap commandMap, final String label) {
        final Command command = commandMap.getCommand(label);
        if (command == null) {
            return;
        }

        commandMap.getKnownCommands().values().removeIf(value -> value == command);

        command.unregister(commandMap);
    }
}