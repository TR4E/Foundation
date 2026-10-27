package me.trae.foundation.minecraft.paper.addon.window;

import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.window.events.WindowOpenEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilTask;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;
import java.util.UUID;

@Getter
public abstract class Window<Layout extends me.trae.foundation.minecraft.paper.addon.window.layout.Layout> implements InventoryHolder {

    private final long openedAt = System.currentTimeMillis();

    private final Layout layout;
    private final UUID viewerId;

    private Inventory inventory;

    private boolean rendering;

    protected Window(final Layout layout, final Player viewer) {
        if (layout.getSize() <= 0) {
            throw new IllegalArgumentException("Window requires a positive layout size");
        }

        this.viewerId = viewer.getUniqueId();
        this.layout = layout;
    }

    @Override
    public final Inventory getInventory() {
        if (this.inventory == null) {
            this.inventory = this.createInventory();
        }

        return this.inventory;
    }

    public final Optional<Player> getViewer() {
        return UtilServer.getOnlinePlayerById(this.viewerId);
    }

    public final void open() {
        final Player viewer = this.getViewer().orElse(null);
        if (viewer == null) {
            return;
        }

        if (UtilEvent.supply(new WindowOpenEvent(this, viewer)).isCancelled() || !this.canOpen(viewer)) {
            return;
        }

        this.render(() -> viewer.openInventory(this.getInventory()));
    }

    public final void close() {
        final Player viewer = this.getViewer().orElse(null);
        if (viewer == null) {
            return;
        }

        if (viewer.getOpenInventory().getTopInventory() != this.getInventory()) {
            return;
        }

        viewer.closeInventory();
    }

    public final void render(final Runnable runnable) {
        if (this.rendering) {
            return;
        }

        this.rendering = true;

        UtilTask.executeAsynchronously(() -> {
            try {
                this.layout.clear();

                this.registerElements(this.layout);
            } finally {
                UtilTask.executeSynchronously(() -> {
                    this.rendering = false;

                    this.layout.applyTo(this.getInventory());

                    if (runnable != null) {
                        runnable.run();
                    }
                });
            }
        });
    }

    public final void refresh() {
        if (this.inventory == null) {
            return;
        }

        this.layout.applyTo(this.getInventory());
    }

    protected abstract Inventory createInventory();

    public abstract Component getTitle();

    protected abstract void registerElements(final Layout layout);

    public boolean canOpen(final Player player) {
        return true;
    }

    public boolean canClose(final Player player) {
        return true;
    }

    public void onOpen(final Player player) {
    }

    public void onClose(final Player player) {
    }
}