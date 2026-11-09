package me.trae.foundation.minecraft.velocity.plugin.framework.utility.registry;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.velocity.plugin.framework.VelocityPlugin;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.AbstractCommand;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.wrapper.BrigadierCommandWrapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
public class CommandRegistry {

    private static final ConcurrentHashMap<CommandMeta, AbstractCommand<?, ?>> commandOwnerMap = new ConcurrentHashMap<>();

    public static void registerCommand(final VelocityPlugin velocityPlugin, final AbstractCommand<?, ?> abstractCommand) {
        if (!abstractCommand.isRoot()) {
            final LinkedHashMap<String, AbstractCommand<?, ?>> childCommandMap = abstractCommand.getParentCommand().getChildCommandMap();

            childCommandMap.put(abstractCommand.getLabel(), abstractCommand);

            abstractCommand.getAliases().forEach(alias -> childCommandMap.put(alias, abstractCommand));

            abstractCommand.getShortcuts().forEach(shortcut -> registerCommand(velocityPlugin, abstractCommand, shortcut, List.of()));

            return;
        }

        registerCommand(velocityPlugin, abstractCommand, abstractCommand.getLabel(), abstractCommand.getAliases());
    }

    private static void registerCommand(final VelocityPlugin velocityPlugin, final AbstractCommand<?, ?> abstractCommand, final String label, final List<String> aliasList) {
        final CommandManager commandManager = velocityPlugin.getProxyServer().getCommandManager();

        removeCommand(commandManager, label);

        aliasList.forEach(alias -> removeCommand(commandManager, alias));

        final CommandMeta commandMeta = commandManager.metaBuilder(label)
                .aliases(aliasList.toArray(String[]::new))
                .plugin(velocityPlugin)
                .build();

        commandManager.register(commandMeta, new BrigadierCommandWrapper(abstractCommand, label).getBrigadierCommand());

        commandOwnerMap.put(commandMeta, abstractCommand);
    }

    public static void unregisterCommand(final VelocityPlugin velocityPlugin, final AbstractCommand<?, ?> abstractCommand) {
        if (!abstractCommand.isRoot()) {
            final LinkedHashMap<String, AbstractCommand<?, ?>> childCommandMap = abstractCommand.getParentCommand().getChildCommandMap();

            childCommandMap.remove(abstractCommand.getLabel(), abstractCommand);

            abstractCommand.getAliases().forEach(alias -> childCommandMap.remove(alias, abstractCommand));
        }

        final CommandManager commandManager = velocityPlugin.getProxyServer().getCommandManager();

        commandOwnerMap.entrySet().stream()
                .filter(entry -> entry.getValue() == abstractCommand)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(commandMeta -> removeCommand(commandManager, commandMeta));
    }

    private static void removeCommand(final CommandManager commandManager, final String alias) {
        final CommandMeta commandMeta = commandManager.getCommandMeta(alias);
        if (commandMeta == null) {
            return;
        }

        removeCommand(commandManager, commandMeta);
    }

    private static void removeCommand(final CommandManager commandManager, final CommandMeta commandMeta) {
        commandMeta.getAliases().stream()
                .filter(alias -> commandManager.getCommandMeta(alias) == commandMeta)
                .toList()
                .forEach(commandManager::unregister);

        commandOwnerMap.remove(commandMeta);
    }
}