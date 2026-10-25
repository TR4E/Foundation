package me.trae.foundation.minecraft.paper.addon.npc.listeners;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.npc.Npc;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPostInteractEvent;
import me.trae.foundation.minecraft.paper.addon.npc.events.NpcPreInteractEvent;
import me.trae.foundation.minecraft.paper.addon.npc.services.NpcService;
import me.trae.foundation.minecraft.paper.addon.npc.types.DamageableNpc;
import me.trae.foundation.minecraft.paper.addon.npc.types.InteractableNpc;
import me.trae.foundation.minecraft.paper.addon.npc.types.enums.NpcInteractType;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

@AllArgsConstructor
@AddonSingleton
public final class NpcInteractListener implements Listener {

    private final NpcService npcService;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttackEntity(final PrePlayerAttackEntityEvent event) {
        this.interact(event, event.getPlayer(), event.getAttacked(), NpcInteractType.LEFT_CLICK);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        this.interact(event, event.getPlayer(), event.getRightClicked(), NpcInteractType.RIGHT_CLICK);
    }

    private void interact(final Cancellable cancellable, final Player player, final Entity entity, final NpcInteractType interactType) {
        final Npc<?> npc = this.npcService.getNpcByEntity(entity).orElse(null);
        if (npc == null) {
            return;
        }

        if (interactType == NpcInteractType.LEFT_CLICK && npc instanceof DamageableNpc) {
            return;
        }

        if (!this.handle(npc, player, entity.getUniqueId(), interactType)) {
            return;
        }

        cancellable.setCancelled(true);
    }

    @SuppressWarnings("unchecked")
    private <E extends Entity> boolean handle(final Npc<E> npc, final Player player, final UUID id, final NpcInteractType interactType) {
        if (!(npc instanceof InteractableNpc<?>)) {
            return false;
        }

        final E entity = npc.getEntityById(id).orElse(null);
        if (entity == null) {
            return false;
        }

        final InteractableNpc<E> interactableNpc = (InteractableNpc<E>) npc;

        if (UtilEvent.supply(new NpcPreInteractEvent(npc, player, entity, interactType)).isCancelled()) {
            return false;
        }

        if (!interactableNpc.canInteract(player, entity, interactType)) {
            return false;
        }

        interactableNpc.onInteract(player, entity, interactType);

        UtilEvent.dispatch(new NpcPostInteractEvent(npc, player, entity, interactType));

        return true;
    }
}