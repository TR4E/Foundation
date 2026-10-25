package me.trae.foundation.minecraft.paper.addon.npc.snapshot;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

public final class NpcEquipmentSnapshot {

    private static final EquipmentSlot[] EQUIPMENT_SLOTS = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Map<EquipmentSlot, ItemStack> itemStackMap = new EnumMap<>(EquipmentSlot.class);
    private final Map<EquipmentSlot, Float> dropChanceMap = new EnumMap<>(EquipmentSlot.class);

    NpcEquipmentSnapshot(final LivingEntity livingEntity) {
        final EntityEquipment entityEquipment = livingEntity.getEquipment();
        if (entityEquipment == null) {
            return;
        }

        for (final EquipmentSlot equipmentSlot : EQUIPMENT_SLOTS) {
            this.itemStackMap.put(equipmentSlot, entityEquipment.getItem(equipmentSlot).clone());

            this.dropChanceMap.put(equipmentSlot, entityEquipment.getDropChance(equipmentSlot));
        }
    }

    public void apply(final LivingEntity livingEntity) {
        final EntityEquipment entityEquipment = livingEntity.getEquipment();
        if (entityEquipment == null) {
            return;
        }

        this.itemStackMap.forEach(entityEquipment::setItem);

        this.dropChanceMap.forEach(entityEquipment::setDropChance);
    }
}