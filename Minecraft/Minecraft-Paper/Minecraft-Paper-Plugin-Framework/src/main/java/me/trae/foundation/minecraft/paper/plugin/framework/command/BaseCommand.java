package me.trae.foundation.minecraft.paper.plugin.framework.command;

import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilMessage;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilPermission;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.function.Function;

@Getter
public abstract class BaseCommand<Parent, Sender extends CommandSender> extends AbstractCommand<Parent, Sender> {

    @Setter
    private static Function<BaseCommand<?, ?>, String> noPermissionMessage = _ -> "You do not have permission to use this command!";

    private final String permission;

    public BaseCommand(final String label, final String description, final List<String> aliases, final String permission) {
        super(label, description, aliases);

        this.permission = permission;
    }

    public BaseCommand(final String label, final String description, final List<String> aliases) {
        super(label, description, aliases);

        this.permission = this.getParentCommand() instanceof final BaseCommand<?, ?> baseCommand ? baseCommand.getPermission() : null;
    }

    public final boolean hasPermission(final CommandSender commandSender, final String permission, final boolean inform) {
        if (UtilPermission.hasPermission(commandSender, permission)) {
            return true;
        }

        if (inform) {
            UtilMessage.message(commandSender, "Permissions", noPermissionMessage.apply(this));
        }

        return false;
    }

    @Override
    public boolean hasPermission(final CommandSender commandSender, final boolean inform) {
        return this.hasPermission(commandSender, this.permission, inform);
    }
}