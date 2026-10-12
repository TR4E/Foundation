package me.trae.foundation.minecraft.paper.plugin.framework.command.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.command.AbstractCommand;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import org.bukkit.command.CommandSender;

@AllArgsConstructor
@Getter
public class CommandExecuteEvent extends CustomCancellableEvent {

    private final AbstractCommand<?, ?> command;
    private final CommandSender sender;
}