package me.trae.foundation.minecraft.velocity.plugin.framework.utility.registry;

import com.velocitypowered.api.command.CommandSource;
import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.velocity.plugin.framework.VelocityPlugin;
import me.trae.foundation.minecraft.velocity.plugin.framework.search.types.VelocityInternalPluginSearchEngine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@UtilityClass
public class PluginRegistry {

    private static final VelocityInternalPluginSearchEngine VELOCITY_INTERNAL_PLUGIN_SEARCH_ENGINE = new VelocityInternalPluginSearchEngine();

    private static final LinkedHashMap<String, VelocityPlugin> internalPluginMap = new LinkedHashMap<>();
    private static final Map<ClassLoader, VelocityPlugin> classLoaderMap = new ConcurrentHashMap<>();

    public static List<VelocityPlugin> getInternalPlugins() {
        return List.copyOf(internalPluginMap.values());
    }

    public static void registerInternalPlugin(final VelocityPlugin velocityPlugin) {
        internalPluginMap.put(velocityPlugin.getClass().getSimpleName().toUpperCase(Locale.ROOT), velocityPlugin);
        classLoaderMap.put(velocityPlugin.getClass().getClassLoader(), velocityPlugin);
    }

    public static void unregisterInternalPlugin(final VelocityPlugin velocityPlugin) {
        internalPluginMap.remove(velocityPlugin.getClass().getSimpleName().toUpperCase(Locale.ROOT));
        classLoaderMap.remove(velocityPlugin.getClass().getClassLoader());
    }

    public static Optional<VelocityPlugin> getInternalPluginByName(final String name) {
        if (name == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(internalPluginMap.get(name.toUpperCase(Locale.ROOT)));
    }

    public static <T extends VelocityPlugin> T getInternalPluginByClass(final Class<T> type) {
        return Optional.ofNullable(internalPluginMap.get(type.getSimpleName().toUpperCase(Locale.ROOT))).filter(type::isInstance).map(type::cast).orElse(null);
    }

    public static VelocityPlugin getSelfPlugin() {
        final String frameworkPackage = VelocityPlugin.class.getPackageName();

        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(type -> !type.getPackageName().startsWith(frameworkPackage))
                        .map(type -> classLoaderMap.get(type.getClassLoader()))
                        .filter(Objects::nonNull)
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException("No plugin found for the calling class"));
    }

    public static Optional<VelocityPlugin> searchInternalPlugin(final CommandSource commandSource, final String input, final boolean inform, final Predicate<VelocityPlugin> predicate) {
        return VELOCITY_INTERNAL_PLUGIN_SEARCH_ENGINE.find(commandSource, input, inform, predicate);
    }

    public static Optional<VelocityPlugin> searchInternalPlugin(final CommandSource commandSource, final String input, final boolean inform) {
        return searchInternalPlugin(commandSource, input, inform, null);
    }
}