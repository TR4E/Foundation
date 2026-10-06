package me.trae.foundation.minecraft.paper.plugin.framework.addon;

import lombok.RequiredArgsConstructor;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.minecraft.paper.plugin.framework.PaperPlugin;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.Addons;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.listener.AddonListener;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.scanner.AddonScanner;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public final class AddonRegistry {

    private final Map<Addon, List<Class<?>>> addonMap = new LinkedHashMap<>();
    private final Set<Addon> hostedAddonSet = new HashSet<>();

    private final PaperPlugin paperPlugin;

    public List<Class<?>> initialize() {
        this.createAddons().forEach(addon -> this.addonMap.put(addon, AddonScanner.scan(addon.getClass())));

        this.addonMap.keySet().forEach(addon -> {
            this.getServicesManager().register(String.class, this.getClaim(addon), this.paperPlugin, ServicePriority.Normal);
        });

        return this.addonMap.entrySet().stream().filter(entry -> this.isElected(entry.getKey())).flatMap(entry -> entry.getValue().stream()).toList();
    }

    public void start() {
        this.addonMap.keySet().stream().filter(this::isElected).forEach(this.hostedAddonSet::add);

        this.paperPlugin.getServer().getPluginManager().registerEvents(new AddonListener(this), this.paperPlugin);
    }

    public void shutdown() {
        this.hostedAddonSet.forEach(addon -> Injector.INSTANCE.detach(this.paperPlugin, this.addonMap.get(addon)));
        this.hostedAddonSet.clear();

        this.getServicesManager().getRegistrations(String.class).stream()
                .filter(registration -> registration.getPlugin() == this.paperPlugin && this.addonMap.keySet().stream().anyMatch(addon -> this.getClaim(addon).equals(registration.getProvider())))
                .toList()
                .forEach(registration -> this.getServicesManager().unregister(String.class, registration.getProvider()));
    }

    public void elect() {
        if (this.paperPlugin.getServer().isStopping() || !this.paperPlugin.isEnabled()) {
            return;
        }

        this.addonMap.forEach((addon, componentList) -> {
            if (this.hostedAddonSet.contains(addon) || !this.isElected(addon)) {
                return;
            }

            Injector.INSTANCE.attach(this.paperPlugin, componentList);

            this.hostedAddonSet.add(addon);
        });
    }

    private List<Addon> createAddons() {
        final Addons addons = this.paperPlugin.getClass().getAnnotation(Addons.class);
        if (addons == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(addons.value()).map(this::createAddon).toList();
    }

    private Addon createAddon(final Class<? extends Addon> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("%s must declare a public no-args constructor".formatted(type.getName()), exception);
        }
    }

    private boolean isElected(final Addon addon) {
        return this.getServicesManager().getRegistrations(String.class).stream()
                .filter(registration -> this.getClaim(addon).equals(registration.getProvider()))
                .findFirst()
                .map(registration -> registration.getPlugin() == this.paperPlugin)
                .orElse(false);
    }

    private String getClaim(final Addon addon) {
        return "addon:%s".formatted(addon.getKey());
    }

    private ServicesManager getServicesManager() {
        return this.paperPlugin.getServer().getServicesManager();
    }
}