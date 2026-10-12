package me.trae.foundation.minecraft.paper.plugin.framework.command.wrapper;

import me.trae.foundation.minecraft.paper.plugin.framework.command.AbstractCommand;
import me.trae.foundation.minecraft.paper.plugin.framework.command.events.CommandExecuteEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.command.events.CommandTabCompleteEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class BukkitCommandWrapper extends BukkitCommand {

    private final AbstractCommand<?, ?> abstractCommand;

    public BukkitCommandWrapper(final AbstractCommand<?, ?> abstractCommand) {
        super(abstractCommand.getLabel(), abstractCommand.getDescription(), abstractCommand.getUsage(), abstractCommand.getAliases());

        this.abstractCommand = abstractCommand;
    }

    @Override
    public boolean execute(@NotNull final CommandSender commandSender, @NotNull final String label, @NotNull final String @NotNull [] args) {
        final AbstractCommand<?, ?> abstractCommand = this.getCommand(this.abstractCommand, args, 0);

        final String[] arguments = this.getArgs(abstractCommand, args);

        return this.executeBaseCommand(abstractCommand, commandSender, arguments);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull final CommandSender commandSender, @NotNull final String alias, @NotNull final String @NotNull [] args) throws IllegalArgumentException {
        final AbstractCommand<?, ?> abstractCommand = this.getCommand(this.abstractCommand, args, 1);

        final String[] arguments = this.getArgs(abstractCommand, args);

        return this.getBaseCommandTabComplete(abstractCommand, commandSender, arguments);
    }

    private AbstractCommand<?, ?> getCommand(final AbstractCommand<?, ?> abstractCommand, final String[] args, final int offset) {
        final int argStart = abstractCommand.getArgStart();
        if (args.length <= argStart + offset) {
            return abstractCommand;
        }

        final AbstractCommand<?, ?> childCommand = abstractCommand.getChildCommandMap().get(args[argStart].toLowerCase(Locale.ROOT));
        if (childCommand == null) {
            return abstractCommand;
        }

        return this.getCommand(childCommand, args, offset);
    }

    private String[] getArgs(final AbstractCommand<?, ?> abstractCommand, final String[] args) {
        return Arrays.copyOfRange(args, abstractCommand.getArgStart(), args.length);
    }

    private <Sender extends CommandSender> boolean executeBaseCommand(final AbstractCommand<?, Sender> abstractCommand, final CommandSender commandSender, final String[] args) {
        if (!abstractCommand.isValidSender(commandSender, true)) {
            return false;
        }

        if (!abstractCommand.hasPermission(commandSender, true)) {
            return false;
        }

        if (UtilEvent.supply(new CommandExecuteEvent(abstractCommand, commandSender)).isCancelled()) {
            return false;
        }

        abstractCommand.execute(abstractCommand.getSenderType().cast(commandSender), args);

        return true;
    }

    private <Sender extends CommandSender> List<String> getBaseCommandTabComplete(final AbstractCommand<?, Sender> abstractCommand, final CommandSender commandSender, final String[] args) {
        if (!abstractCommand.isValidSender(commandSender, false)) {
            return Collections.emptyList();
        }

        if (!abstractCommand.hasPermission(commandSender, false)) {
            return Collections.emptyList();
        }

        if (UtilEvent.supply(new CommandTabCompleteEvent(abstractCommand, commandSender)).isCancelled()) {
            return Collections.emptyList();
        }

        return abstractCommand.getTabComplete(abstractCommand.getSenderType().cast(commandSender), args);
    }
}