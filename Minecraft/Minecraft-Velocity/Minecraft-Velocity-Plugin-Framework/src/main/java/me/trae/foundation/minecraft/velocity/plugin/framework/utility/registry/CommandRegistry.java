package me.trae.foundation.minecraft.velocity.plugin.framework.utility.registry;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.velocity.plugin.framework.VelocityPlugin;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.AbstractCommand;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.wrapper.BrigadierCommandWrapper;

import java.util.LinkedHashMap;

@UtilityClass
public class CommandRegistry {

    public static void registerCommand(final VelocityPlugin velocityPlugin, final AbstractCommand<?, ?> abstractCommand) {
        if (!abstractCommand.isRoot()) {
            final LinkedHashMap<String, AbstractCommand<?, ?>> childCommandMap = abstractCommand.getParentCommand().getChildCommandMap();

            childCommandMap.put(abstractCommand.getLabel(), abstractCommand);

            abstractCommand.getAliases().forEach(alias -> childCommandMap.put(alias, abstractCommand));
            return;
        }

        final CommandManager commandManager = velocityPlugin.getProxyServer().getCommandManager();

        removeCommand(commandManager, abstractCommand.getLabel());

        abstractCommand.getAliases().forEach(alias -> removeCommand(commandManager, alias));

        final CommandMeta commandMeta = commandManager.metaBuilder(abstractCommand.getLabel())
                .aliases(abstractCommand.getAliases().toArray(String[]::new))
                .plugin(velocityPlugin)
                .build();

        commandManager.register(commandMeta, new BrigadierCommandWrapper(abstractCommand).getBrigadierCommand());
    }

    public static void unregisterCommand(final VelocityPlugin velocityPlugin, final AbstractCommand<?, ?> abstractCommand) {
        if (!abstractCommand.isRoot()) {
            final LinkedHashMap<String, AbstractCommand<?, ?>> childCommandMap = abstractCommand.getParentCommand().getChildCommandMap();

            childCommandMap.remove(abstractCommand.getLabel(), abstractCommand);

            abstractCommand.getAliases().forEach(alias -> childCommandMap.remove(alias, abstractCommand));
            return;
        }

        removeCommand(velocityPlugin.getProxyServer().getCommandManager(), abstractCommand.getLabel());
    }

    private static void removeCommand(final CommandManager commandManager, final String alias) {
        final CommandMeta commandMeta = commandManager.getCommandMeta(alias);
        if (commandMeta == null) {
            return;
        }

        commandManager.unregister(commandMeta);
    }
}