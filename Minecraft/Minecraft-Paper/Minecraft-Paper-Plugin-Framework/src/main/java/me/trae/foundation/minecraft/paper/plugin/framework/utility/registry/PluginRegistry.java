package me.trae.foundation.minecraft.paper.plugin.framework.utility.registry;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.paper.plugin.framework.PaperPlugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@UtilityClass
public class PluginRegistry {

    private static final LinkedHashMap<String, PaperPlugin> internalPluginMap = new LinkedHashMap<>();

    public static List<PaperPlugin> getInternalPlugins() {
        return List.copyOf(internalPluginMap.values());
    }

    public static void registerInternalPlugin(final PaperPlugin paperPlugin) {
        internalPluginMap.put(paperPlugin.getName().toUpperCase(Locale.ROOT), paperPlugin);
    }

    public static void unregisterInternalPlugin(final PaperPlugin paperPlugin) {
        internalPluginMap.remove(paperPlugin.getName().toUpperCase(Locale.ROOT));
    }

    public static Optional<PaperPlugin> getInternalPluginByName(final String name) {
        if (name == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(internalPluginMap.get(name.toUpperCase(Locale.ROOT)));
    }

    public static <T extends PaperPlugin> T getInternalPluginByClass(final Class<T> type) {
        return JavaPlugin.getPlugin(type);
    }

    public static PaperPlugin getSelfPlugin() {
        return JavaPlugin.getPlugin(PaperPlugin.class);
    }
}