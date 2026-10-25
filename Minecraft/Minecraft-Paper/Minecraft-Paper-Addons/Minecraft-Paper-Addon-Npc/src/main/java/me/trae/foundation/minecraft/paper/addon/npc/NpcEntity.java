package me.trae.foundation.minecraft.paper.addon.npc;

import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.npc.snapshot.NpcSnapshot;
import org.bukkit.Location;

@Getter
@Setter
public final class NpcEntity<Entity extends org.bukkit.entity.Entity> {

    private Entity entity;

    private NpcSnapshot snapshot;

    private Location spawnedAtLocation, lastKnownLocation;

    public NpcEntity(final Entity entity, final Location location) {
        this.entity = entity;
        this.spawnedAtLocation = location.clone();
        this.lastKnownLocation = location.clone();
    }
}