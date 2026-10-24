package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damager.WeaponDurabilityEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Weapon;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

@AddonSingleton
public final class WeaponDurabilityListener implements Listener {

    @EventHandler
    public void onDamageApplied(final DamageAppliedEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();
        if (damageEvent.isCancelled() || damageEvent.getBlockedDamage() > 0.0D || damageEvent.getCause() != DamageCause.ENTITY_ATTACK || damageEvent.getProjectile() != null) {
            return;
        }

        if (!CraftLivingEntity.class.isInstance(damageEvent.getDamager())) {
            return;
        }

        final LivingEntity damager = CraftLivingEntity.class.cast(damageEvent.getDamager()).getHandle();

        final ItemStack itemStack = damager.getMainHandItem();

        final Weapon weapon = itemStack.get(DataComponents.WEAPON);
        if (weapon == null || !itemStack.isDamageableItem()) {
            return;
        }

        final WeaponDurabilityEvent durabilityEvent = UtilEvent.supply(new WeaponDurabilityEvent(damageEvent, CraftItemStack.asBukkitCopy(itemStack), weapon.itemDamagePerAttack()));
        if (durabilityEvent.isCancelled() || durabilityEvent.getAmount() <= 0) {
            return;
        }

        itemStack.hurtAndBreak(durabilityEvent.getAmount(), damager, EquipmentSlot.MAINHAND);
    }
}