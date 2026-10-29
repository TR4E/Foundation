package me.trae.foundation.minecraft.velocity.plugin.framework.utility;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class UtilCommandSource {

    private static final UUID CONSOLE_ID = new UUID(0L, 0L);
    private static final String CONSOLE_NAME = "Console";

    public static UUID getId(final CommandSource commandSource) {
        if (commandSource instanceof ConsoleCommandSource) {
            return CONSOLE_ID;
        }

        if (commandSource instanceof final Player player) {
            return player.getUniqueId();
        }

        return null;
    }

    public static String getName(final CommandSource commandSource) {
        if (commandSource instanceof ConsoleCommandSource) {
            return CONSOLE_NAME;
        }

        if (commandSource instanceof final Player player) {
            return player.getUsername();
        }

        return null;
    }
}