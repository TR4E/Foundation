package me.trae.foundation.minecraft.paper.addon.npc;

import com.destroystokyo.paper.entity.ai.MobGoals;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPostDespawnEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPostRestoreEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPostSpawnEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPreDespawnEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPreRestoreEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPreSpawnEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcUnloadEvent;
import me.trae.foundation.minecraft.paper.addon.npc.goals.FollowPathGoal;
import me.trae.foundation.minecraft.paper.addon.npc.goals.LookAtEntityGoal;
import me.trae.foundation.minecraft.paper.addon.npc.snapshot.NpcSnapshot;
import me.trae.foundation.minecraft.paper.addon.npc.types.DamageableNpc;
import me.trae.foundation.minecraft.paper.addon.npc.types.EquippableNpc;
import me.trae.foundation.minecraft.paper.addon.npc.types.LookingNpc;
import me.trae.foundation.minecraft.paper.addon.npc.types.MovableNpc;
import me.trae.foundation.minecraft.paper.addon.npc.types.NameableNpc;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEntity;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilPdc;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
public abstract class Npc<Entity extends org.bukkit.entity.Entity> {

    public static final NamespacedKey NPC_KEY = new NamespacedKey("custom", "npc");

    private final LinkedHashMap<UUID, NpcEntity<Entity>> entityMap = new LinkedHashMap<>();

    private final List<NpcEntity<Entity>> pendingList = new ArrayList<>();

    @Getter
    private final Class<Entity> entityClass;

    @Getter
    private final String identifier;

    public final List<Entity> getEntities() {
        return this.entityMap.values().stream().map(NpcEntity::getEntity).toList();
    }

    public final Optional<NpcEntity<Entity>> getNpcEntityById(final UUID id) {
        return Optional.ofNullable(this.entityMap.get(id));
    }

    public final Optional<Entity> getEntityById(final UUID id) {
        return this.getNpcEntityById(id).map(NpcEntity::getEntity);
    }

    public final void spawn(final Location location) {
        if (UtilEvent.supply(new NpcPreSpawnEvent(this, location)).isCancelled()) {
            return;
        }

        if (!this.canSpawn(location)) {
            return;
        }

        final Entity entity = this.create(location);

        this.entityMap.put(entity.getUniqueId(), new NpcEntity<>(entity, location));

        this.onSpawn(entity);

        UtilEvent.dispatch(new NpcPostSpawnEvent(this, entity));
    }

    public final void despawn(final UUID entityId) {
        final NpcEntity<Entity> npcEntity = this.entityMap.get(entityId);
        if (npcEntity == null) {
            return;
        }

        final Entity entity = npcEntity.getEntity();

        if (UtilEvent.supply(new NpcPreDespawnEvent(this, entity)).isCancelled()) {
            return;
        }

        if (!this.canDespawn(entity)) {
            return;
        }

        this.onDespawn(entity);

        entity.remove();

        this.entityMap.remove(entityId);

        UtilEvent.dispatch(new NpcPostDespawnEvent(this, entity));
    }

    public final void discard(final UUID entityId) {
        this.entityMap.remove(entityId);
    }

    public final void load(final Chunk chunk) {
        this.pendingList.removeIf(npcEntity -> {
            if (!this.isWithin(npcEntity.getLastKnownLocation(), chunk)) {
                return false;
            }

            if (this instanceof MovableNpc) {
                npcEntity.setSpawnedAtLocation(npcEntity.getLastKnownLocation().clone());
            }

            return this.restore(npcEntity);
        });
    }

    public final void unload(final UUID entityId, final Location lastKnownLocation) {
        final NpcEntity<Entity> npcEntity = this.entityMap.remove(entityId);
        if (npcEntity == null) {
            return;
        }

        npcEntity.setLastKnownLocation(lastKnownLocation);

        if (npcEntity.getEntity() instanceof final LivingEntity livingEntity) {
            npcEntity.setSnapshot(this.createSnapshot(livingEntity));
        }

        this.pendingList.add(npcEntity);

        UtilEvent.dispatch(new NpcUnloadEvent(this, npcEntity.getEntity()));
    }

    public final void respawn(final UUID entityId) {
        final NpcEntity<Entity> npcEntity = this.entityMap.remove(entityId);
        if (npcEntity == null) {
            return;
        }

        npcEntity.setSnapshot(null);

        this.restore(npcEntity);
    }

    public final void purge(final Entity entity) {
        entity.remove();

        this.entityMap.remove(entity.getUniqueId());
    }

    public final void purgeAll() {
        this.pendingList.clear();

        this.getEntities().forEach(this::purge);
    }

    protected NpcSnapshot createSnapshot(final LivingEntity livingEntity) {
        return new NpcSnapshot(livingEntity);
    }

    public void onSetup(final Entity entity) {
    }

    public void onSpawn(final Entity entity) {
    }

    public void onDespawn(final Entity entity) {
    }

    public boolean canSpawn(final Location location) {
        return true;
    }

    public boolean canDespawn(final Entity entity) {
        return true;
    }

    private Entity create(final Location location) {
        final Entity entity = location.getWorld().spawn(location, this.entityClass, CreatureSpawnEvent.SpawnReason.CUSTOM, spawned -> {
            spawned.setPersistent(false);

            this.setup(spawned);

            this.onSetup(spawned);
        });

        UtilPdc.setPersistentDataType(entity.getPersistentDataContainer(), NPC_KEY, PersistentDataType.STRING, this.identifier);

        return entity;
    }

    private boolean isWithin(final Location location, final Chunk chunk) {
        if (!chunk.getWorld().equals(location.getWorld())) {
            return false;
        }

        if (chunk.getX() != location.getBlockX() >> 4) {
            return false;
        }

        if (chunk.getZ() != location.getBlockZ() >> 4) {
            return false;
        }

        return true;
    }

    private boolean restore(final NpcEntity<Entity> npcEntity) {
        final Location location = npcEntity.getSpawnedAtLocation();

        if (UtilEvent.supply(new NpcPreRestoreEvent(this, location)).isCancelled()) {
            return false;
        }

        if (!this.canSpawn(location)) {
            return false;
        }

        final Entity entity = this.create(location);

        if (npcEntity.getSnapshot() != null && entity instanceof final LivingEntity livingEntity) {
            npcEntity.getSnapshot().apply(livingEntity);

            npcEntity.setSnapshot(null);
        }

        npcEntity.setEntity(entity);

        this.entityMap.put(entity.getUniqueId(), npcEntity);

        this.onSpawn(entity);

        UtilEvent.dispatch(new NpcPostRestoreEvent(this, entity));

        return true;
    }

    private void setup(final Entity entity) {
        entity.setGravity(false);

        entity.setInvulnerable(true);

        if (entity instanceof final LivingEntity livingEntity) {
            livingEntity.setCollidable(false);

            livingEntity.setRemoveWhenFarAway(false);

            if (this instanceof final DamageableNpc damageableNpc) {
                entity.setInvulnerable(false);

                livingEntity.setCollidable(true);

                UtilEntity.setAttributeBaseValue(livingEntity, Attribute.MAX_HEALTH, damageableNpc.getMaxHealth());

                livingEntity.setHealth(Math.clamp(damageableNpc.getDefaultHealth(), 1.0D, damageableNpc.getMaxHealth()));
            }

            if (this instanceof final EquippableNpc equippableNpc) {
                final EntityEquipment entityEquipment = livingEntity.getEquipment();
                if (entityEquipment != null) {
                    entityEquipment.setItemInMainHand(equippableNpc.getItemInMainHand());

                    entityEquipment.setItemInOffHand(equippableNpc.getItemInOffHand());

                    entityEquipment.setArmorContents(equippableNpc.getArmourContents());
                }
            }
        }

        if (entity instanceof final Mob mob) {
            final MobGoals mobGoals = Bukkit.getServer().getMobGoals();

            mobGoals.removeAllGoals(mob);

            if (this instanceof final LookingNpc lookingNpc) {
                mobGoals.addGoal(mob, 0, new LookAtEntityGoal(lookingNpc.getLookingAtType(), mob, lookingNpc.getLookingDistance(), lookingNpc.getLookingProbability(), lookingNpc.isLookingOnlyHorizontal()));
            }

            if (this instanceof final MovableNpc movableNpc) {
                entity.setGravity(movableNpc.hasGravity());

                mobGoals.addGoal(mob, 1, new FollowPathGoal(mob, movableNpc.getPathLocations(), movableNpc.isPathLooping(), movableNpc.getMovementSpeed(), movableNpc.getPathWaitTicks()));
            }
        }

        if (this instanceof final NameableNpc nameableNpc) {
            entity.customName(nameableNpc.getName());

            entity.setCustomNameVisible(nameableNpc.isNameAlwaysVisible());
        }
    }
}