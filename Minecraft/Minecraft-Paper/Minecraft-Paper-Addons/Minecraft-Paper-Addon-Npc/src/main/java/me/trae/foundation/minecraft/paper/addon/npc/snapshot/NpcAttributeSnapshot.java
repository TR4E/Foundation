package me.trae.foundation.minecraft.paper.addon.npc.snapshot;

import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NpcAttributeSnapshot {

    private final Map<Attribute, Collection<AttributeModifier>> modifierMap = new HashMap<>();

    NpcAttributeSnapshot(final LivingEntity livingEntity) {
        for (final Attribute attribute : Registry.ATTRIBUTE) {
            final AttributeInstance attributeInstance = livingEntity.getAttribute(attribute);
            if (attributeInstance == null) {
                continue;
            }

            if (attributeInstance.getModifiers().isEmpty()) {
                continue;
            }

            this.modifierMap.put(attribute, List.copyOf(attributeInstance.getModifiers()));
        }
    }

    public void apply(final LivingEntity livingEntity) {
        this.modifierMap.forEach((attribute, attributeModifierCollection) -> {
            final AttributeInstance attributeInstance = livingEntity.getAttribute(attribute);
            if (attributeInstance == null) {
                return;
            }

            attributeModifierCollection.forEach(attributeModifier -> {
                attributeInstance.removeModifier(attributeModifier.getKey());

                attributeInstance.addModifier(attributeModifier);
            });
        });
    }
}