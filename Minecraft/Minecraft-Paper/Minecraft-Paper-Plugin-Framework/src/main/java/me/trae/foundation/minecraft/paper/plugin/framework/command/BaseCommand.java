package me.trae.foundation.minecraft.paper.plugin.framework.command;

import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilMessage;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilPermission;
import me.trae.foundation.utilities.UtilGeneric;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

@Getter
public abstract class BaseCommand<Parent, Sender extends CommandSender> {

    public static final Comparator<Class<?>> COMPARATOR_ORDER = Comparator.comparing(type -> {
        final List<Class<?>> path = new ArrayList<>();

        for (Class<?> current = type; BaseCommand.class.isAssignableFrom(current); current = UtilGeneric.getGenericParameter(current, BaseCommand.class, 0)) {
            path.addFirst(current);
        }

        return path;
    }, (firstPath, secondPath) -> {
        for (int index = 0; index < Math.min(firstPath.size(), secondPath.size()); index++) {
            final int compare = firstPath.get(index).getName().compareTo(secondPath.get(index).getName());
            if (compare != 0) {
                return compare;
            }
        }

        return Integer.compare(firstPath.size(), secondPath.size());
    });

    @Setter
    private static Function<BaseCommand<?, ?>, String> invalidCommandSenderMessage = _ -> "Invalid Command Sender!";

    @Setter
    private static Function<BaseCommand<?, ?>, String> noPermissionMessage = _ -> "You do not have permission to use this command!";

    @SuppressWarnings("unchecked")
    private final Class<Parent> parentType = (Class<Parent>) UtilGeneric.getGenericParameter(this.getClass(), BaseCommand.class, 0);

    @SuppressWarnings("unchecked")
    private final Class<Sender> senderType = (Class<Sender>) UtilGeneric.getGenericParameter(this.getClass(), BaseCommand.class, 1);

    private final String label, description;
    private final List<String> aliases;

    private String permission;

    private final LinkedHashMap<String, BaseCommand<?, ?>> childCommandMap = new LinkedHashMap<>();

    public BaseCommand(final String label, final String description, final List<String> aliases, final String permission) {
        this.label = label;
        this.description = description;
        this.aliases = aliases;
        this.permission = permission;
    }

    public BaseCommand(final String label, final String description, final List<String> aliases) {
        this(label, description, aliases, null);

        final BaseCommand<?, ?> parentCommand = this.getParentCommand();

        this.permission = parentCommand != null ? parentCommand.getPermission() : null;
    }

    public final Parent getParent() {
        return Injector.INSTANCE.get(this.parentType);
    }

    public final boolean isRoot() {
        return !BaseCommand.class.isAssignableFrom(this.parentType);
    }

    public final BaseCommand<?, ?> getParentCommand() {
        return BaseCommand.class.isInstance(this.getParent()) ? BaseCommand.class.cast(this.getParent()) : null;
    }

    public final int getArgStart() {
        return this.isRoot() ? 0 : this.getParentCommand().getArgStart() + 1;
    }

    public String getUsage() {
        return "/" + this.getLabel();
    }

    public final boolean isValidSender(final CommandSender commandSender, final Class<? extends CommandSender> type, final boolean inform) {
        if (type.isInstance(commandSender)) {
            return true;
        }

        if (inform) {
            UtilMessage.message(commandSender, "Command", invalidCommandSenderMessage.apply(this));
        }

        return false;
    }

    public final boolean isValidSender(final CommandSender commandSender, final boolean inform) {
        return this.isValidSender(commandSender, this.senderType, inform);
    }

    public final boolean hasPermission(final CommandSender commandSender, final String permission, final boolean inform) {
        if (this.validatePermission(commandSender, permission)) {
            return true;
        }

        if (inform) {
            UtilMessage.message(commandSender, "Permissions", noPermissionMessage.apply(this));
        }

        return false;
    }

    public final boolean hasPermission(final CommandSender commandSender, final boolean inform) {
        return this.hasPermission(commandSender, this.permission, inform);
    }

    public boolean validatePermission(final CommandSender commandSender, final String permission) {
        return UtilPermission.hasPermission(commandSender, permission);
    }

    public abstract void execute(final Sender sender, final String[] args);

    public List<String> getTabComplete(final Sender sender, final String[] args) {
        return Collections.emptyList();
    }
}