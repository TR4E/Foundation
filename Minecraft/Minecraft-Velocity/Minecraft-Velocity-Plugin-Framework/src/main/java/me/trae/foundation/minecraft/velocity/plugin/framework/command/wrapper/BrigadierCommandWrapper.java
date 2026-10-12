package me.trae.foundation.minecraft.velocity.plugin.framework.command.wrapper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import lombok.Getter;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.AbstractCommand;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.events.CommandExecuteEvent;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.events.CommandTabCompleteEvent;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.UtilEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class BrigadierCommandWrapper {

    private final AbstractCommand<?, ?> abstractCommand;

    @Getter
    private final BrigadierCommand brigadierCommand;

    public BrigadierCommandWrapper(final AbstractCommand<?, ?> abstractCommand) {
        this.abstractCommand = abstractCommand;

        this.brigadierCommand = new BrigadierCommand(BrigadierCommand.literalArgumentBuilder(abstractCommand.getLabel())
                .executes(context -> this.execute(context.getSource(), ""))
                .then(BrigadierCommand.requiredArgumentBuilder("args", StringArgumentType.greedyString())
                        .suggests(this::suggest)
                        .executes(context -> this.execute(context.getSource(), StringArgumentType.getString(context, "args"))))
                .build());
    }

    private int execute(final CommandSource commandSource, final String input) {
        final String[] args = input.isEmpty() ? new String[0] : input.split(" ");

        final AbstractCommand<?, ?> abstractCommand = this.getCommand(this.abstractCommand, args, 0);

        final String[] arguments = this.getArgs(abstractCommand, args);

        return this.executeBaseCommand(abstractCommand, commandSource, arguments) ? Command.SINGLE_SUCCESS : 0;
    }

    private CompletableFuture<Suggestions> suggest(final CommandContext<CommandSource> context, final SuggestionsBuilder suggestionsBuilder) {
        final String remaining = suggestionsBuilder.getRemaining();

        final String[] args = remaining.split(" ", -1);

        final AbstractCommand<?, ?> abstractCommand = this.getCommand(this.abstractCommand, args, 1);

        final String[] arguments = this.getArgs(abstractCommand, args);

        final StringRange stringRange = StringRange.between(suggestionsBuilder.getStart() + remaining.lastIndexOf(' ') + 1, suggestionsBuilder.getInput().length());

        final List<Suggestion> suggestions = this.getBaseCommandTabComplete(abstractCommand, context.getSource(), arguments).stream()
                .map(value -> new Suggestion(stringRange, value))
                .toList();

        return CompletableFuture.completedFuture(new Suggestions(stringRange, suggestions));
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

    private <Sender extends CommandSource> boolean executeBaseCommand(final AbstractCommand<?, Sender> abstractCommand, final CommandSource commandSource, final String[] args) {
        if (!abstractCommand.isValidSender(commandSource, true)) {
            return false;
        }

        if (!abstractCommand.hasPermission(commandSource, true)) {
            return false;
        }

        if (UtilEvent.supply(new CommandExecuteEvent(abstractCommand, commandSource)).isCancelled()) {
            return false;
        }

        abstractCommand.execute(abstractCommand.getSenderType().cast(commandSource), args);

        return true;
    }

    private <Sender extends CommandSource> List<String> getBaseCommandTabComplete(final AbstractCommand<?, Sender> abstractCommand, final CommandSource commandSource, final String[] args) {
        if (!abstractCommand.isValidSender(commandSource, false)) {
            return Collections.emptyList();
        }

        if (!abstractCommand.hasPermission(commandSource, false)) {
            return Collections.emptyList();
        }

        if (UtilEvent.supply(new CommandTabCompleteEvent(abstractCommand, commandSource)).isCancelled()) {
            return Collections.emptyList();
        }

        return abstractCommand.getTabComplete(abstractCommand.getSenderType().cast(commandSource), args);
    }
}