package me.trae.foundation.minecraft.paper.addon.npc.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.minecraft.paper.addon.npc.Npc;
import me.trae.foundation.minecraft.paper.addon.npc.search.NpcSearchEngine;
import me.trae.foundation.minecraft.paper.addon.npc.types.RespawnableNpc;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilTask;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@AllArgsConstructor
@AddonSingleton
public final class NpcService implements Lifecycle {

    private final NpcSearchEngine npcSearchEngine = new NpcSearchEngine(this);

    private final Map<String, Npc<?>> npcMap = new HashMap<>();

    @Getter
    private final List<Npc<?>> npcList;

    @Override
    public void onLiveDependencyUpdate() {
        this.npcMap.clear();

        for (final Npc<?> npc : this.npcList) {
            this.npcMap.put(npc.getIdentifier(), npc);
        }
    }

    @Override
    public void onComponentShutdown() {
        this.npcList.forEach(Npc::purgeAll);
    }

    public Optional<Npc<?>> getNpcByIdentifier(final String identifier) {
        return Optional.ofNullable(this.npcMap.get(identifier));
    }

    public Optional<Npc<?>> getNpcByEntity(final Entity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(Npc.NPC_KEY, PersistentDataType.STRING)).flatMap(this::getNpcByIdentifier);
    }

    public void load(final Chunk chunk) {
        this.npcList.forEach(npc -> npc.load(chunk));
    }

    public void unload(final Entity entity) {
        this.getNpcByEntity(entity).ifPresent(npc -> npc.unload(entity.getUniqueId(), entity.getLocation()));
    }

    public void discard(final Entity entity) {
        this.getNpcByEntity(entity).ifPresent(npc -> npc.discard(entity.getUniqueId()));
    }

    public void death(final Entity entity) {
        final UUID entityId = entity.getUniqueId();

        this.getNpcByEntity(entity).ifPresent(npc -> {
            if (!(npc instanceof final RespawnableNpc respawnableNpc)) {
                npc.discard(entityId);

                return;
            }

            UtilTask.executeLaterSynchronously(respawnableNpc.getRespawnDelay(), respawnableNpc.getRespawnInterval(), () -> npc.respawn(entityId));
        });
    }

    public Optional<Npc<?>> searchNpc(final CommandSender commandSender, final String input, final boolean inform, final Predicate<Npc<?>> predicate) {
        return this.npcSearchEngine.find(commandSender, input, inform, predicate);
    }

    public Optional<Npc<?>> searchNpc(final CommandSender commandSender, final String input, final boolean inform) {
        return this.searchNpc(commandSender, input, inform, null);
    }
}