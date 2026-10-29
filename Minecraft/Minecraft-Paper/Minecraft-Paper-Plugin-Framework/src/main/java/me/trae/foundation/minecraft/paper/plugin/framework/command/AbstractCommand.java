package me.trae.foundation.minecraft.paper.plugin.framework.command;

import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilMessage;
import me.trae.foundation.utilities.UtilGeneric;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

@Getter
public abstract class AbstractCommand<Parent, Sender extends CommandSender> {

    public static final Comparator<Class<?>> COMPARATOR_ORDER = Comparator.comparing(type -> {
        final List<Class<?>> path = new ArrayList<>();

        for (Class<?> current = type; AbstractCommand.class.isAssignableFrom(current); current = UtilGeneric.getGenericParameter(current, AbstractCommand.class, 0)) {
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
    private static Function<AbstractCommand<?, ?>, String> invalidCommandSenderMessage = _ -> "Invalid Command Sender!";

    @SuppressWarnings("unchecked")
    private final Class<Parent> parentType = (Class<Parent>) UtilGeneric.getGenericParameter(this.getClass(), AbstractCommand.class, 0);

    @SuppressWarnings("unchecked")
    private final Class<Sender> senderType = (Class<Sender>) UtilGeneric.getGenericParameter(this.getClass(), AbstractCommand.class, 1);

    private final String label, description;
    private final List<String> aliases;

    private final LinkedHashMap<String, AbstractCommand<?, ?>> childCommandMap = new LinkedHashMap<>();

    public AbstractCommand(final String label, final String description, final List<String> aliases) {
        this.label = label;
        this.description = description;
        this.aliases = aliases;
    }

    public final Parent getParent() {
        return Injector.INSTANCE.get(this.parentType);
    }

    public final boolean isRoot() {
        return !AbstractCommand.class.isAssignableFrom(this.parentType);
    }

    public final AbstractCommand<?, ?> getParentCommand() {
        return AbstractCommand.class.isInstance(this.getParent()) ? AbstractCommand.class.cast(this.getParent()) : null;
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

    public abstract boolean hasPermission(final CommandSender commandSender, final boolean inform);

    public abstract void execute(final Sender sender, final String[] args);

    public List<String> getTabComplete(final Sender sender, final String[] args) {
        if (args.length == 1 && !this.getChildCommandMap().isEmpty()) {
            return Suggestions.childCommands(this, sender, args[0]);
        }

        return Collections.emptyList();
    }
}