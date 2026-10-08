package me.trae.foundation.minecraft.velocity.plugin.framework.utility.registry;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.velocity.plugin.framework.VelocityPlugin;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.BaseCommand;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.wrapper.BrigadierCommandWrapper;

import java.util.LinkedHashMap;

@UtilityClass
public class CommandRegistry {

    public static void registerCommand(final VelocityPlugin velocityPlugin, final BaseCommand<?, ?> baseCommand) {
        if (!baseCommand.isRoot()) {
            final LinkedHashMap<String, BaseCommand<?, ?>> childCommandMap = baseCommand.getParentCommand().getChildCommandMap();

            childCommandMap.put(baseCommand.getLabel(), baseCommand);

            baseCommand.getAliases().forEach(alias -> childCommandMap.put(alias, baseCommand));
            return;
        }

        final CommandManager commandManager = velocityPlugin.getProxyServer().getCommandManager();

        removeCommand(commandManager, baseCommand.getLabel());

        baseCommand.getAliases().forEach(alias -> removeCommand(commandManager, alias));

        final CommandMeta commandMeta = commandManager.metaBuilder(baseCommand.getLabel())
                .aliases(baseCommand.getAliases().toArray(String[]::new))
                .plugin(velocityPlugin)
                .build();

        commandManager.register(commandMeta, new BrigadierCommandWrapper(baseCommand).getBrigadierCommand());
    }

    public static void unregisterCommand(final VelocityPlugin velocityPlugin, final BaseCommand<?, ?> baseCommand) {
        if (!baseCommand.isRoot()) {
            final LinkedHashMap<String, BaseCommand<?, ?>> childCommandMap = baseCommand.getParentCommand().getChildCommandMap();

            childCommandMap.remove(baseCommand.getLabel(), baseCommand);

            baseCommand.getAliases().forEach(alias -> childCommandMap.remove(alias, baseCommand));
            return;
        }

        removeCommand(velocityPlugin.getProxyServer().getCommandManager(), baseCommand.getLabel());
    }

    private static void removeCommand(final CommandManager commandManager, final String alias) {
        final CommandMeta commandMeta = commandManager.getCommandMeta(alias);
        if (commandMeta == null) {
            return;
        }

        commandManager.unregister(commandMeta);
    }
}