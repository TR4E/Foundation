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
import me.trae.foundation.minecraft.velocity.plugin.framework.command.BaseCommand;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.events.CommandExecuteEvent;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.events.CommandTabCompleteEvent;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.UtilEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class BrigadierCommandWrapper {

    private final BaseCommand<?, ?> baseCommand;

    @Getter
    private final BrigadierCommand brigadierCommand;

    public BrigadierCommandWrapper(final BaseCommand<?, ?> baseCommand) {
        this.baseCommand = baseCommand;

        this.brigadierCommand = new BrigadierCommand(BrigadierCommand.literalArgumentBuilder(baseCommand.getLabel())
                .executes(context -> this.execute(context.getSource(), ""))
                .then(BrigadierCommand.requiredArgumentBuilder("args", StringArgumentType.greedyString())
                        .suggests(this::suggest)
                        .executes(context -> this.execute(context.getSource(), StringArgumentType.getString(context, "args"))))
                .build());
    }

    private int execute(final CommandSource commandSource, final String input) {
        final String[] args = input.isEmpty() ? new String[0] : input.split(" ");

        final BaseCommand<?, ?> baseCommand = this.getCommand(this.baseCommand, args, 0);

        final String[] arguments = this.getArgs(baseCommand, args);

        return this.executeBaseCommand(baseCommand, commandSource, arguments) ? Command.SINGLE_SUCCESS : 0;
    }

    private CompletableFuture<Suggestions> suggest(final CommandContext<CommandSource> context, final SuggestionsBuilder suggestionsBuilder) {
        final String remaining = suggestionsBuilder.getRemaining();

        final String[] args = remaining.split(" ", -1);

        final BaseCommand<?, ?> baseCommand = this.getCommand(this.baseCommand, args, 1);

        final String[] arguments = this.getArgs(baseCommand, args);

        final StringRange stringRange = StringRange.between(suggestionsBuilder.getStart() + remaining.lastIndexOf(' ') + 1, suggestionsBuilder.getInput().length());

        final List<Suggestion> suggestions = this.getBaseCommandTabComplete(baseCommand, context.getSource(), arguments).stream()
                .map(value -> new Suggestion(stringRange, value))
                .toList();

        return CompletableFuture.completedFuture(new Suggestions(stringRange, suggestions));
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

    private <Sender extends CommandSource> boolean executeBaseCommand(final BaseCommand<?, Sender> baseCommand, final CommandSource commandSource, final String[] args) {
        if (!baseCommand.isValidSender(commandSource, true)) {
            return false;
        }

        if (!baseCommand.hasPermission(commandSource, true)) {
            return false;
        }

        if (UtilEvent.supply(new CommandExecuteEvent(baseCommand, commandSource)).isCancelled()) {
            return false;
        }

        baseCommand.execute(baseCommand.getSenderType().cast(commandSource), args);

        return true;
    }

    private <Sender extends CommandSource> List<String> getBaseCommandTabComplete(final BaseCommand<?, Sender> baseCommand, final CommandSource commandSource, final String[] args) {
        if (!baseCommand.isValidSender(commandSource, false)) {
            return Collections.emptyList();
        }

        if (!baseCommand.hasPermission(commandSource, false)) {
            return Collections.emptyList();
        }

        if (UtilEvent.supply(new CommandTabCompleteEvent(baseCommand, commandSource)).isCancelled()) {
            return Collections.emptyList();
        }

        return baseCommand.getTabComplete(baseCommand.getSenderType().cast(commandSource), args);
    }
}