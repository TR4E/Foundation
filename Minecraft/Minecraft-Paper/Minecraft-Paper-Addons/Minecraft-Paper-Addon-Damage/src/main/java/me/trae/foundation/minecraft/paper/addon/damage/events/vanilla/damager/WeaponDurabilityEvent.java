package me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damager;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@Getter
@Setter
public final class WeaponDurabilityEvent extends CustomCancellableEvent {

    private final CustomDamageEvent damageEvent;
    private final ItemStack itemStack;

    private int amount;
}