package me.trae.foundation.minecraft.paper.addon.item;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.common.color.ChatColor;
import me.trae.foundation.minecraft.paper.addon.item.events.ItemMetaUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.item.events.ItemStackUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.item.styles.ItemStyle;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilColor;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilMessage;
import me.trae.foundation.utilities.UtilJava;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Getter
public abstract class Item {

    private final Material material;

    protected void stamp(final ItemMeta itemMeta) {
    }

    protected void editMeta(final ItemMeta itemMeta) {
    }

    protected boolean hideAttributes() {
        return false;
    }

    protected int getMaxStackSize() {
        return this.material.getMaxStackSize();
    }

    protected Boolean addGlow() {
        return null;
    }

    protected ItemStyle getStyle() {
        return null;
    }

    public NamespacedKey getModel() {
        return null;
    }

    public NamespacedKey getTooltipStyle() {
        return this.getStyle() != null ? this.getStyle().getTooltipStyle() : null;
    }

    public Color getColor() {
        return this.getStyle() != null ? this.getStyle().getColor() : ChatColor.WHITE.getColor();
    }

    public List<TextDecoration> getDecorations() {
        return this.getStyle() != null ? this.getStyle().getDecorations() : null;
    }

    public abstract String getName();

    public abstract List<String> getLore();

    protected Component getDisplayName() {
        if (this.getName() == null) {
            return null;
        }

        final Style style = Style.style(builder -> {
            builder.color(UtilColor.toTextColor(this.getColor()));

            if (this.getDecorations() != null) {
                this.getDecorations().forEach(decoration -> builder.decoration(decoration, true));
            }
        });

        return UtilMessage.deserialize(this.getName()).applyFallbackStyle(style).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public final boolean isSimilarByMaterial(final ItemStack itemStack) {
        return itemStack != null && this.material == itemStack.getType();
    }

    public final ItemStack create(final int amount, final int durability) {
        return this.build(amount, durability, true);
    }

    public final ItemStack create(final int amount) {
        return this.create(amount, 0);
    }

    public final ItemStack create() {
        return this.create(1, 0);
    }

    public final ItemStack create(final ItemStack itemStack) {
        final int durability = itemStack.getItemMeta() instanceof final Damageable damageable ? damageable.getDamage() : 0;

        return this.create(itemStack.getAmount(), durability);
    }

    public final ItemStack createView() {
        return this.build(1, 0, false);
    }

    private ItemStack build(final int amount, final int durability, final boolean stamp) {
        final ItemStack itemStack = ItemStack.of(this.getMaterial(), amount);

        itemStack.editMeta(itemMeta -> {
            if (itemMeta instanceof final Damageable damageable && durability > 0) {
                damageable.setDamage(durability);
            }

            this.applyItemMeta(itemMeta, stamp);

            UtilEvent.dispatch(new ItemMetaUpdateEvent(this, itemMeta));
        });

        UtilEvent.dispatch(new ItemStackUpdateEvent(this, itemStack));

        return itemStack;
    }

    public final ItemStack update(final ItemStack itemStack) {
        final boolean requiresMaterialUpdate = itemStack.getType() != this.material;

        final ItemStack newItemStack = requiresMaterialUpdate ? itemStack.withType(this.getMaterial()) : itemStack;

        newItemStack.editMeta(itemMeta -> {
            if (requiresMaterialUpdate) {
                this.updateDurability(itemStack, newItemStack, itemMeta);
            }

            this.applyItemMeta(itemMeta, true);

            UtilEvent.dispatch(new ItemMetaUpdateEvent(this, itemMeta));
        });

        UtilEvent.dispatch(new ItemStackUpdateEvent(this, newItemStack));

        return newItemStack;
    }

    public final ItemStack refresh(final ItemStack itemStack) {
        UtilEvent.dispatch(new ItemStackUpdateEvent(this, itemStack));

        return itemStack;
    }

    private void applyItemMeta(final ItemMeta itemMeta, final boolean stamp) {
        // Stamp
        if (stamp) {
            this.stamp(itemMeta);
        }

        // Display Name
        itemMeta.displayName(this.getDisplayName());

        // Lore
        final List<String> lore = UtilJava.createCollection(new ArrayList<>(this.getLore()), list -> {
            if (this.getStyle() != null && this.getStyle().getTag() != null) {
                if (!list.isEmpty()) {
                    list.add("");
                }

                list.add("<font:custom:tags>%s</font>".formatted(this.getStyle().getTag()));
            }
        });

        itemMeta.lore(lore.stream().map(line -> UtilMessage.deserialize(line).applyFallbackStyle(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)).toList());

        // Model
        itemMeta.setItemModel(this.getModel());

        // Tooltip Style
        itemMeta.setTooltipStyle(this.getTooltipStyle());

        // Hide Attributes
        if (this.hideAttributes()) {
            itemMeta.addItemFlags(ItemFlag.values());
        } else {
            itemMeta.removeItemFlags(ItemFlag.values());
        }

        // Max Stack Size
        itemMeta.setMaxStackSize(this.getMaxStackSize() != this.material.getMaxStackSize() ? this.getMaxStackSize() : null);

        // Add Glow
        itemMeta.setEnchantmentGlintOverride(this.addGlow());

        // Edit Meta
        this.editMeta(itemMeta);
    }

    private void updateDurability(final ItemStack itemStack, final ItemStack newItemStack, final ItemMeta itemMeta) {
        if (!(itemStack.getItemMeta() instanceof final Damageable oldDamageable) || !(itemMeta instanceof final Damageable newDamageable)) {
            return;
        }

        final int oldMaxDurability = itemStack.getType().getMaxDurability();
        final int newMaxDurability = newItemStack.getType().getMaxDurability();

        if (oldMaxDurability <= 0 || newMaxDurability <= 0) {
            return;
        }

        final int oldDamage = oldDamageable.getDamage();
        final int oldRemaining = oldMaxDurability - oldDamage;

        final int newRemaining;

        if (newMaxDurability > oldMaxDurability) {
            // Upgrade: preserve percentage remaining, giving the player
            // the additional uses provided by the stronger material.
            final double remainingPercentage = (double) oldRemaining / oldMaxDurability;

            newRemaining = (int) Math.round(remainingPercentage * newMaxDurability);
        } else {
            // Downgrade: preserve actual remaining uses without exceeding
            // what the weaker material can represent.
            newRemaining = Math.min(oldRemaining, newMaxDurability);
        }

        newDamageable.setDamage(newMaxDurability - Math.max(1, newRemaining));
    }
}