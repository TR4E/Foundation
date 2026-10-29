package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.ArmourSlot;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damagee.ArmourDurabilityEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.tag.DamageTypeTags;

@AddonSingleton
public final class ArmourDurabilityListener implements Listener {

    private static final float HARD_HAT_MULTIPLIER = 0.75F;

    @SuppressWarnings("UnstableApiUsage")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.ARMOUR) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !damageEvent.hasDamage() || !CraftPlayer.class.isInstance(damageEvent.getDamagee()) || !CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return;
        }

        final ServerPlayer serverPlayer = CraftPlayer.class.cast(damageEvent.getDamagee()).getHandle();

        final DamageSource damageSource = CraftDamageSource.class.cast(damageEvent.getSource()).getHandle();

        final float damage = (float) damageEvent.getDamage();

        if (DamageTypeTags.DAMAGES_HELMET.isTagged(damageEvent.getSource().getDamageType()) && !serverPlayer.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            this.hurtEquipment(damageEvent, serverPlayer, damageSource, damage / HARD_HAT_MULTIPLIER, ArmourSlot.HELMET);
        }

        if (!DamageTypeTags.BYPASSES_ARMOR.isTagged(damageEvent.getSource().getDamageType())) {
            this.hurtEquipment(damageEvent, serverPlayer, damageSource, damage, ArmourSlot.values());
        }
    }

    private void hurtEquipment(final CustomDamageEvent damageEvent, final ServerPlayer serverPlayer, final DamageSource damageSource, final float damage, final ArmourSlot... armourSlots) {
        final int amount = (int) Math.max(1.0F, damage / 4.0F);

        for (final ArmourSlot armourSlot : armourSlots) {
            final EquipmentSlot equipmentSlot = switch (armourSlot) {
                case BOOTS -> EquipmentSlot.FEET;
                case LEGGINGS -> EquipmentSlot.LEGS;
                case CHESTPLATE -> EquipmentSlot.CHEST;
                case HELMET -> EquipmentSlot.HEAD;
            };

            final ItemStack itemStack = serverPlayer.getItemBySlot(equipmentSlot);

            final Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);

            if (equippable == null || !equippable.damageOnHurt() || !itemStack.isDamageableItem() || !itemStack.canBeHurtBy(damageSource)) {
                continue;
            }

            final ArmourDurabilityEvent durabilityEvent = UtilEvent.supply(new ArmourDurabilityEvent(damageEvent, armourSlot, CraftItemStack.asBukkitCopy(itemStack), amount));

            if (durabilityEvent.isCancelled() || durabilityEvent.getAmount() <= 0) {
                continue;
            }

            itemStack.hurtAndBreak(durabilityEvent.getAmount(), serverPlayer, equipmentSlot);
        }
    }
}