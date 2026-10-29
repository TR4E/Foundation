package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

@UtilityClass
public class UtilCommandSender {

    private static final UUID CONSOLE_ID = new UUID(0L, 0L);
    private static final String CONSOLE_NAME = "Console";

    public static UUID getId(final CommandSender commandSender) {
        if (commandSender instanceof ConsoleCommandSender) {
            return CONSOLE_ID;
        }

        if (commandSender instanceof final Player player) {
            return player.getUniqueId();
        }

        return null;
    }

    public static String getName(final CommandSender commandSender) {
        if (commandSender instanceof ConsoleCommandSender) {
            return CONSOLE_NAME;
        }

        if (commandSender instanceof final Player player) {
            return player.getName();
        }

        return null;
    }
}