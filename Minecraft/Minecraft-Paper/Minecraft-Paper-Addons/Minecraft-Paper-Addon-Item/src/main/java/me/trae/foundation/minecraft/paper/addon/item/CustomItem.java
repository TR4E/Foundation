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
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class CustomItem extends Item {

    public static final NamespacedKey VERSION_KEY = new NamespacedKey("custom", "item_version");
    public static final NamespacedKey DELETABLE_KEY = new NamespacedKey("custom", "item_deletable");
    public static final NamespacedKey TRACKING_ID_KEY = new NamespacedKey("custom", "item_tracking_id");

    @Getter
    private final String uniqueId, identifier;

    private String version;

    public CustomItem(final Material material, final String uniqueId, final String identifier) {
        super(material);

        this.uniqueId = uniqueId;
        this.identifier = identifier;
    }

    private String getVersion() {
        if (this.version == null) {
            this.version = UtilHash.hashToString("SHA-256", String.join("\u0000", this.generateVersionEntries()));
        }

        return this.version;
    }

    @Override
    protected final void stamp(final ItemMeta itemMeta) {
        UtilItemStack.setPersistentDataType(itemMeta, UtilItemStack.UNIQUE_ID_KEY, PersistentDataType.STRING, this.uniqueId);
        UtilItemStack.setPersistentDataType(itemMeta, VERSION_KEY, PersistentDataType.STRING, this.getVersion());

        UtilItemStack.setPersistentDataType(itemMeta, DELETABLE_KEY, PersistentDataType.BOOLEAN, this.deleteIfRemoved());

        if (this.isTrackable() && this.getMaxStackSize() == 1) {
            if (UtilItemStack.getPersistentData(itemMeta, TRACKING_ID_KEY, PersistentDataType.STRING).isEmpty()) {
                UtilItemStack.setPersistentDataType(itemMeta, TRACKING_ID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            }
        } else {
            UtilItemStack.removePersistentDataType(itemMeta, TRACKING_ID_KEY);
        }
    }

    @Override
    protected boolean hideAttributes() {
        return true;
    }

    public boolean isInvincible() {
        return false;
    }

    public boolean canDespawn() {
        return true;
    }

    public boolean isTrackable() {
        return false;
    }

    public boolean removeVanillaUseCooldown() {
        return true;
    }

    public boolean naturallyObtainable() {
        return false;
    }

    protected boolean deleteIfRemoved() {
        return false;
    }

    protected List<String> generateVersionEntries() {
        final List<TextDecoration> textDecorationList = this.getDecorations();

        return List.of(
                // Item
                UtilString.pair("Material", this.getMaterial().name()),
                UtilString.pair("Hide-Attributes", Boolean.toString(this.hideAttributes())),
                UtilString.pair("Max-Stack-Size", Integer.toString(this.getMaxStackSize())),
                UtilString.pair("Add-Glow", this.addGlow() != null ? Boolean.toString(this.addGlow()) : ""),
                UtilString.pair("Style-Name", this.getStyle() != null ? this.getStyle().getName() : ""),
                UtilString.pair("Style-Tag", this.getStyle() != null ? this.getStyle().getTag() : ""),
                UtilString.pair("Model", this.getModel() != null ? this.getModel().asString() : ""),
                UtilString.pair("Tooltip-Style", this.getTooltipStyle() != null ? this.getTooltipStyle().asString() : ""),
                UtilString.pair("Color", Integer.toString(this.getColor().getRGB())),
                UtilString.pair("Decorations", textDecorationList != null ? textDecorationList.stream().map(TextDecoration::name).collect(Collectors.joining("\u0001")) : ""),
                UtilString.pair("Name", this.getName()),
                UtilString.pair("Lore", String.join("\u0001", this.getLore())),

                // Custom Item
                UtilString.pair("Trackable", Boolean.toString(this.isTrackable())),
                UtilString.pair("Delete-If-Removed", Boolean.toString(this.deleteIfRemoved()))
        );
    }

    public final boolean isSimilarByUniqueId(final ItemStack itemStack) {
        return UtilItemStack.getPersistentData(itemStack, UtilItemStack.UNIQUE_ID_KEY, PersistentDataType.STRING)
                .map(this.uniqueId::equals)
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
                .map(this::isSimilarByUniqueId)
                .orElse(false);
    }
}