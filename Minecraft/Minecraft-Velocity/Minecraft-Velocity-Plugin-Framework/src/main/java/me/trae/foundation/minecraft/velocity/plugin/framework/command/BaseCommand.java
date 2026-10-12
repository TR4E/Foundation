package me.trae.foundation.minecraft.velocity.plugin.framework.command;

import com.velocitypowered.api.command.CommandSource;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.UtilMessage;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.UtilPermission;

import java.util.List;
import java.util.function.Function;

@Getter
public abstract class BaseCommand<Parent, Sender extends CommandSource> extends AbstractCommand<Parent, Sender> {

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

    public final boolean hasPermission(final CommandSource commandSource, final String permission, final boolean inform) {
        if (UtilPermission.hasPermission(commandSource, permission)) {
            return true;
        }

        if (inform) {
            UtilMessage.message(commandSource, "Permissions", noPermissionMessage.apply(this));
        }

        return false;
    }

    @Override
    public boolean hasPermission(final CommandSource commandSource, final boolean inform) {
        return this.hasPermission(commandSource, this.permission, inform);
    }
}