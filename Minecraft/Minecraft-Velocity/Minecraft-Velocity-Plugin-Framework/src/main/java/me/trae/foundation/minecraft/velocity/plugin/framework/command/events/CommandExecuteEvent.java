package me.trae.foundation.minecraft.velocity.plugin.framework.command.events;

import com.velocitypowered.api.command.CommandSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.AbstractCommand;
import me.trae.foundation.minecraft.velocity.plugin.framework.event.CustomCancellableEvent;

@AllArgsConstructor
@Getter
public class CommandExecuteEvent extends CustomCancellableEvent {

    private final AbstractCommand<?, ?> command;
    private final CommandSource sender;
}