package me.trae.foundation.minecraft.paper.addon.item;

import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import me.trae.foundation.utilities.UtilHash;
import me.trae.foundation.utilities.UtilString;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class CustomItem extends Item {

    public static final NamespacedKey VERSION_KEY = new NamespacedKey("custom", "item_version");
    public static final NamespacedKey DELETABLE_KEY = new NamespacedKey("custom", "item_deletable");

    @Getter
    private final String identifier, namespace;

    private String version;

    public CustomItem(final Material material, final String identifier, final String namespace) {
        super(material);

        this.identifier = identifier;
        this.namespace = namespace;
    }

    private String getVersion() {
        if (this.version == null) {
            this.version = UtilHash.hashToString("SHA-256", String.join("\u0000", this.generateVersionEntries()));
        }

        return this.version;
    }

    @Override
    protected final void stamp(final ItemMeta itemMeta) {
        UtilItemStack.setPersistentDataType(itemMeta, UtilItemStack.IDENTIFIER_KEY, PersistentDataType.STRING, this.identifier);
        UtilItemStack.setPersistentDataType(itemMeta, VERSION_KEY, PersistentDataType.STRING, this.getVersion());

        UtilItemStack.setPersistentDataType(itemMeta, DELETABLE_KEY, PersistentDataType.BOOLEAN, this.deleteIfRemoved());
    }

    @Override
    protected boolean hideAttributes() {
        return true;
    }

    public boolean removeUseCooldown() {
        return true;
    }

    public boolean usableInRecipes() {
        return false;
    }

    public boolean naturallyObtainable() {
        return false;
    }

    protected boolean deleteIfRemoved() {
        return false;
    }

    protected List<String> generateVersionEntries() {
        return List.of(
                UtilString.pair("Material", this.getMaterial().name()),
                UtilString.pair("Name", this.getName()),
                UtilString.pair("Lore", String.join("\u0001", this.getLore())),
                UtilString.pair("Color", Integer.toString(this.getColor().getRGB())),
                UtilString.pair("Decorations", this.getDecorations() != null ? this.getDecorations().stream().map(TextDecoration::name).collect(Collectors.joining("\u0001")) : ""),
                UtilString.pair("Model", this.getModel() != null ? this.getModel().asString() : ""),
                UtilString.pair("Tooltip-Style", this.getTooltipStyle() != null ? this.getTooltipStyle().asString() : ""),
                UtilString.pair("Hide-Attributes", Boolean.toString(this.hideAttributes())),
                UtilString.pair("Max-Stack-Size", Integer.toString(this.getMaxStackSize())),
                UtilString.pair("Add-Glow", this.addGlow() != null ? Boolean.toString(this.addGlow()) : ""),
                UtilString.pair("Remove-Use-Cooldown", Boolean.toString(this.removeUseCooldown())),
                UtilString.pair("Usable-In-Recipes", Boolean.toString(this.usableInRecipes())),
                UtilString.pair("Naturally-Obtainable", Boolean.toString(this.naturallyObtainable())),
                UtilString.pair("Delete-If-Removed", Boolean.toString(this.deleteIfRemoved())),
                UtilString.pair("Style-Name", this.getStyle() != null ? this.getStyle().getName() : ""),
                UtilString.pair("Style-Tag", this.getStyle() != null ? this.getStyle().getTag() : "")
        );
    }

    public final boolean isSimilarByIdentifier(final ItemStack itemStack) {
        return UtilItemStack.getPersistentData(itemStack, UtilItemStack.IDENTIFIER_KEY, PersistentDataType.STRING)
                .map(this.identifier::equals)
                .orElse(false);
    }

    public final boolean isOutdatedByItemStack(final ItemStack itemStack) {
        return UtilItemStack.getPersistentData(itemStack, VERSION_KEY, PersistentDataType.STRING)
                .map(version -> !this.getVersion().equals(version))
                .orElse(true);
    }

    public final boolean isHolding(final LivingEntity livingEntity) {
        return Optional.ofNullable(livingEntity.getEquipment())
                .map(EntityEquipment::getItemInMainHand)
                .map(this::isSimilarByIdentifier)
                .orElse(false);
    }
}