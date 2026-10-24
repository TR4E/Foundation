package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.ArmourSlot;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damagee.ArmourReductionEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.tag.DamageTypeTags;

@AddonSingleton
public final class ArmourReductionListener implements Listener {

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.ARMOUR) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || DamageTypeTags.BYPASSES_ARMOR.isTagged(damageEvent.getSource().getDamageType())) {
            return;
        }

        if (!CraftLivingEntity.class.isInstance(damageEvent.getDamagee()) || !CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        final DamageSource damageSource = CraftDamageSource.class.cast(damageEvent.getSource()).getHandle();

        float armour = entity.getArmorValue();

        float toughness = (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

        final ItemStack[] armourContents = damageEvent.getArmourContents();

        if (armourContents != null) {
            final ArmourSlot[] armourSlots = ArmourSlot.values();

            for (int index = 0; index < Math.min(armourContents.length, armourSlots.length); index++) {
                final ItemStack itemStack = armourContents[index];

                if (itemStack == null || itemStack.isEmpty()) {
                    continue;
                }

                final ArmourSlot armourSlot = armourSlots[index];

                final net.minecraft.world.entity.EquipmentSlot nmsEquipmentSlot = switch (armourSlot) {
                    case BOOTS -> net.minecraft.world.entity.EquipmentSlot.FEET;
                    case LEGGINGS -> net.minecraft.world.entity.EquipmentSlot.LEGS;
                    case CHESTPLATE -> net.minecraft.world.entity.EquipmentSlot.CHEST;
                    case HELMET -> net.minecraft.world.entity.EquipmentSlot.HEAD;
                    default -> throw new IllegalStateException("Unsupported armour slot: " + armourSlot);
                };

                final ItemAttributeModifiers modifiers = CraftItemStack.asNMSCopy(itemStack).get(DataComponents.ATTRIBUTE_MODIFIERS);

                final double[] defaults = new double[2];

                if (modifiers != null) {
                    modifiers.forEach(nmsEquipmentSlot, (attribute, modifier) -> {
                        if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) {
                            return;
                        }

                        if (attribute.equals(Attributes.ARMOR)) {
                            defaults[0] += modifier.amount();
                        }

                        if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) {
                            defaults[1] += modifier.amount();
                        }
                    });
                }

                final ArmourReductionEvent armourReductionEvent = UtilEvent.supply(new ArmourReductionEvent(damageEvent, armourSlot, itemStack, damageEvent.getDamage(), defaults[0], defaults[1]));

                armour += (float) ((armourReductionEvent.isCancelled() ? 0.0D : armourReductionEvent.getPoints()) - defaults[0]);
                toughness += (float) ((armourReductionEvent.isCancelled() ? 0.0D : armourReductionEvent.getToughness()) - defaults[1]);
            }
        }

        damageEvent.setDamage(CombatRules.getDamageAfterAbsorb(entity, (float) damageEvent.getDamage(), damageSource, armour, toughness));
    }
}