package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

@UtilityClass
public class UtilPdc {

    public static <Primitive, Complex> void setPersistentDataType(final PersistentDataContainer persistentDataContainer, final NamespacedKey namespacedKey, final PersistentDataType<Primitive, Complex> persistentDataType, final Complex value) {
        persistentDataContainer.set(namespacedKey, persistentDataType, value);
    }

    public static void removePersistentDataType(final PersistentDataContainer persistentDataContainer, final NamespacedKey namespacedKey) {
        persistentDataContainer.remove(namespacedKey);
    }

    public static <Primitive, Complex> Optional<Complex> getPersistentData(final PersistentDataContainer persistentDataContainer, final NamespacedKey namespacedKey, final PersistentDataType<Primitive, Complex> persistentDataType) {
        return Optional.ofNullable(persistentDataContainer.get(namespacedKey, persistentDataType));
    }

    public static PersistentDataContainer getOrCreatePersistentDataContainer(final PersistentDataContainer persistentDataContainer, final NamespacedKey namespacedKey) {
        return getPersistentData(persistentDataContainer, namespacedKey, PersistentDataType.TAG_CONTAINER).orElseGet(persistentDataContainer.getAdapterContext()::newPersistentDataContainer);
    }
}