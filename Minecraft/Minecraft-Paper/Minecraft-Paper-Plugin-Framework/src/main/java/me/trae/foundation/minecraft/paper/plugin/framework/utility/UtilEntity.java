package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;

import java.util.Optional;

@UtilityClass
public class UtilEntity {

    public static Optional<AttributeInstance> getAttribute(final Entity entity, final Attribute attribute) {
        if (!(entity instanceof final Attributable attributable)) {
            return Optional.empty();
        }

        return Optional.ofNullable(attributable.getAttribute(attribute));
    }

    public static double getAttributeValue(final Entity entity, final Attribute attribute, final double fallback) {
        return getAttribute(entity, attribute).map(AttributeInstance::getValue).orElse(fallback);
    }

    public static void setAttributeBaseValue(final Entity entity, final Attribute attribute, final double value) {
        getAttribute(entity, attribute).ifPresent(attributeInstance -> attributeInstance.setBaseValue(value));
    }

    public static void addAttributeModifier(final Entity entity, final Attribute attribute, final AttributeModifier attributeModifier) {
        getAttribute(entity, attribute).ifPresent(attributeInstance -> {
            attributeInstance.removeModifier(attributeModifier.getKey());
            attributeInstance.addModifier(attributeModifier);
        });
    }

    public static void removeAttributeModifier(final Entity entity, final Attribute attribute, final NamespacedKey namespacedKey) {
        getAttribute(entity, attribute).ifPresent(attributeInstance -> attributeInstance.removeModifier(namespacedKey));
    }
}