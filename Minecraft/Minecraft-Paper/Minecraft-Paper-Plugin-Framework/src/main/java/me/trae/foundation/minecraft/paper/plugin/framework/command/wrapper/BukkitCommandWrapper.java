package me.trae.foundation.minecraft.paper.plugin.framework.command.wrapper;

import me.trae.foundation.minecraft.paper.plugin.framework.command.BaseCommand;
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

    private final BaseCommand<?, ?> baseCommand;

    public BukkitCommandWrapper(final BaseCommand<?, ?> baseCommand) {
        super(baseCommand.getLabel(), baseCommand.getDescription(), baseCommand.getUsage(), baseCommand.getAliases());

        this.baseCommand = baseCommand;
    }

    @Override
    public boolean execute(@NotNull final CommandSender commandSender, @NotNull final String label, @NotNull final String @NotNull [] args) {
        final BaseCommand<?, ?> baseCommand = this.getCommand(this.baseCommand, args, 0);

        final String[] arguments = this.getArgs(baseCommand, args);

        return this.executeBaseCommand(baseCommand, commandSender, arguments);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull final CommandSender commandSender, @NotNull final String alias, @NotNull final String @NotNull [] args) throws IllegalArgumentException {
        final BaseCommand<?, ?> baseCommand = this.getCommand(this.baseCommand, args, 1);

        final String[] arguments = this.getArgs(baseCommand, args);

        return this.getBaseCommandTabComplete(baseCommand, commandSender, arguments);
    }

    private BaseCommand<?, ?> getCommand(final BaseCommand<?, ?> baseCommand, final String[] args, final int offset) {
        final int argStart = baseCommand.getArgStart();
        if (args.length <= argStart + offset) {
            return baseCommand;
        }

        final BaseCommand<?, ?> childCommand = baseCommand.getChildCommandMap().get(args[argStart].toLowerCase(Locale.ROOT));
        if (childCommand == null) {
            return baseCommand;
        }

        return this.getCommand(childCommand, args, offset);
    }

    private String[] getArgs(final BaseCommand<?, ?> baseCommand, final String[] args) {
        return Arrays.copyOfRange(args, baseCommand.getArgStart(), args.length);
    }

    private <Sender extends CommandSender> boolean executeBaseCommand(final BaseCommand<?, Sender> baseCommand, final CommandSender commandSender, final String[] args) {
        if (!baseCommand.isValidSender(commandSender, true)) {
            return false;
        }

        if (!baseCommand.hasPermission(commandSender, true)) {
            return false;
        }

        if (UtilEvent.supply(new CommandExecuteEvent(baseCommand, commandSender)).isCancelled()) {
            return false;
        }

        baseCommand.execute(baseCommand.getSenderType().cast(commandSender), args);

        return true;
    }

    private <Sender extends CommandSender> List<String> getBaseCommandTabComplete(final BaseCommand<?, Sender> baseCommand, final CommandSender commandSender, final String[] args) {
        if (!baseCommand.isValidSender(commandSender, false)) {
            return Collections.emptyList();
        }

        if (!baseCommand.hasPermission(commandSender, false)) {
            return Collections.emptyList();
        }

        if (UtilEvent.supply(new CommandTabCompleteEvent(baseCommand, commandSender)).isCancelled()) {
            return Collections.emptyList();
        }

        return baseCommand.getTabComplete(baseCommand.getSenderType().cast(commandSender), args);
    }
}