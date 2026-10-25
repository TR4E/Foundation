package me.trae.foundation.minecraft.paper.addon.npc.types;

import me.trae.foundation.minecraft.paper.addon.npc.types.enums.NpcInteractType;
import org.bukkit.entity.Player;

public interface InteractableNpc<Entity extends org.bukkit.entity.Entity> {

    void onInteract(final Player player, final Entity entity, final NpcInteractType interactType);

    default boolean canInteract(final Player player, final Entity entity, final NpcInteractType interactType) {
        return true;
    }
}