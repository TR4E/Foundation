package me.trae.foundation.minecraft.paper.addon.window.element;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.common.message.BaseUtilMessage;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilPdc;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@AllArgsConstructor
@Getter
public abstract class Element<Window extends me.trae.foundation.minecraft.paper.addon.window.Window<?>> {

    public static final NamespacedKey ELEMENT_KEY = new NamespacedKey("custom", "element_item");

    private final Window window;
    private final ItemStack itemStack;

    public final ItemStack toItemStack() {
        final ItemStack itemStack = this.itemStack.clone();

        itemStack.editMeta(itemMeta -> {
            UtilPdc.setPersistentDataType(itemMeta.getPersistentDataContainer(), ELEMENT_KEY, PersistentDataType.BOOLEAN, true);

            final List<String> description = this.getDescription();

            if (description != null && !description.isEmpty()) {
                final List<Component> lore = new ArrayList<>(Objects.requireNonNullElse(itemMeta.lore(), Collections.emptyList()));

                if (!lore.isEmpty()) {
                    lore.add(Component.empty());
                }

                description.forEach(line -> lore.add(BaseUtilMessage.deserialize(line).applyFallbackStyle(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));

                itemMeta.lore(lore);
            }
        });

        return itemStack;
    }

    public abstract List<String> getDescription();
}