package me.trae.foundation.minecraft.paper.addon.npc.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.npc.services.NpcService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilTask;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.time.temporal.ChronoUnit;

@AllArgsConstructor
@AddonSingleton
public final class NpcCleanupListener implements Listener {

    private final NpcService npcService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(final EntityRemoveEvent event) {
        final Entity entity = event.getEntity();

        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD) {
            this.npcService.unload(entity);
            return;
        }

        if (event.getCause() == EntityRemoveEvent.Cause.DEATH) {
            this.npcService.death(entity);
            return;
        }

        this.npcService.discard(entity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(final ChunkLoadEvent event) {
        UtilTask.executeLaterSynchronously(50, ChronoUnit.MILLIS, () -> this.npcService.load(event.getChunk()));
    }
}