package me.trae.foundation.minecraft.paper.addon.window;

import lombok.Getter;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.minecraft.paper.addon.window.events.WindowOpenEvent;
import me.trae.foundation.minecraft.paper.addon.window.services.WindowService;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilTask;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Getter
public abstract class Window<Layout extends me.trae.foundation.minecraft.paper.addon.window.layout.Layout> implements InventoryHolder {

    private final long openedAt = System.currentTimeMillis();

    private final Layout layout;
    private final UUID viewerId;

    private Inventory inventory;

    private UUID requestId;

    private volatile boolean rendering;

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
        UtilTask.executeSynchronously(() -> {
            if (this.rendering || this.getViewer().isEmpty()) {
                return;
            }

            final WindowService windowService = this.getWindowService();

            if (windowService.isClosingAll()) {
                return;
            }

            final UUID requestId = windowService.beginRequest(this.viewerId);

            this.requestId = requestId;

            try {
                UtilTask.executeLaterSynchronously(50, ChronoUnit.MILLIS, () -> this.open(windowService, requestId));
            } catch (final RuntimeException | Error throwable) {
                windowService.completeRequest(this.viewerId, requestId);

                throw throwable;
            }
        });
    }

    public final void close() {
        UtilTask.executeSynchronously(() -> {
            final Player viewer = this.getViewer().orElse(null);
            if (viewer == null) {
                return;
            }

            final WindowService windowService = this.getWindowService();

            if (windowService.isClosingAll()) {
                return;
            }

            if (viewer.getOpenInventory().getTopInventory() != this.inventory && (this.requestId == null || !windowService.isCurrentRequest(this.viewerId, this.requestId))) {
                return;
            }

            final UUID requestId = windowService.beginRequest(this.viewerId);

            this.requestId = requestId;

            try {
                UtilTask.executeLaterSynchronously(50, ChronoUnit.MILLIS, () -> {
                    if (!windowService.isCurrentRequest(this.viewerId, requestId)) {
                        return;
                    }

                    windowService.completeRequest(this.viewerId, requestId);

                    this.getViewer().ifPresent(player -> {
                        windowService.closeInventory(player, this.inventory, InventoryCloseEvent.Reason.PLUGIN);
                    });
                });
            } catch (final RuntimeException | Error throwable) {
                windowService.completeRequest(this.viewerId, requestId);

                throw throwable;
            }
        });
    }

    public final void render(final Runnable runnable) {
        if (this.rendering) {
            return;
        }

        this.rendering = true;

        try {
            UtilTask.executeAsynchronously(() -> {
                try {
                    this.layout.clear();

                    this.registerElements(this.layout);
                } catch (final RuntimeException | Error throwable) {
                    UtilTask.executeSynchronously(this::abort);

                    throw throwable;
                }

                UtilTask.executeSynchronously(() -> {
                    try {
                        this.layout.applyTo(this.getInventory());

                        if (runnable != null) {
                            runnable.run();
                        }

                        this.rendering = false;
                    } catch (final RuntimeException | Error throwable) {
                        this.abort();

                        throw throwable;
                    }
                });
            });
        } catch (final RuntimeException | Error throwable) {
            this.rendering = false;

            throw throwable;
        }
    }

    public final void refresh() {
        if (this.rendering || this.inventory == null) {
            return;
        }

        try {
            this.layout.applyTo(this.inventory);
        } catch (final RuntimeException | Error throwable) {
            this.abort();

            throw throwable;
        }
    }

    protected abstract Inventory createInventory();

    protected abstract Component getTitle();

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

    private WindowService getWindowService() {
        return Injector.INSTANCE.get(WindowService.class);
    }

    private void open(final WindowService windowService, final UUID requestId) {
        if (!windowService.isCurrentRequest(this.viewerId, requestId)) {
            return;
        }

        final Player viewer = this.getViewer().orElse(null);
        if (viewer == null || this.rendering) {
            windowService.completeRequest(this.viewerId, requestId);
            return;
        }

        try {
            if (UtilEvent.supply(new WindowOpenEvent(this, viewer)).isCancelled() || !this.canOpen(viewer)
                    || !windowService.isCurrentRequest(this.viewerId, requestId) || this.rendering) {
                windowService.completeRequest(this.viewerId, requestId);
                return;
            }

            this.render(() -> windowService.openInventory(this.viewerId, requestId, this.getInventory()));
        } catch (final RuntimeException | Error throwable) {
            windowService.completeRequest(this.viewerId, requestId);

            throw throwable;
        }
    }

    private void abort() {
        this.rendering = true;

        try {
            this.layout.clear();

            if (this.requestId != null) {
                this.getWindowService().completeRequest(this.viewerId, this.requestId);
            }

            UtilTask.executeLaterSynchronously(50, ChronoUnit.MILLIS, () -> {
                try {
                    this.getViewer().ifPresent(viewer -> {
                        this.getWindowService().closeInventory(
                                viewer,
                                this.inventory,
                                InventoryCloseEvent.Reason.CANT_USE
                        );
                    });
                } finally {
                    this.rendering = false;
                }
            });
        } catch (final RuntimeException | Error throwable) {
            this.rendering = false;

            throw throwable;
        }
    }
}