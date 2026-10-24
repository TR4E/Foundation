package me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damagee;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.damage.enums.ArmourSlot;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@Getter
@Setter
public final class ArmourReductionEvent extends CustomCancellableEvent {

    private final CustomDamageEvent damageEvent;
    private final ArmourSlot slot;
    private final ItemStack itemStack;
    private final double damage;

    private double points, toughness;
}