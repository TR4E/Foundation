package me.trae.foundation.minecraft.paper.plugin.framework.command.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.command.BaseCommand;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import org.bukkit.command.CommandSender;

@AllArgsConstructor
@Getter
public class CommandTabCompleteEvent extends CustomCancellableEvent {

    private final BaseCommand<?, ?> command;
    private final CommandSender sender;
}