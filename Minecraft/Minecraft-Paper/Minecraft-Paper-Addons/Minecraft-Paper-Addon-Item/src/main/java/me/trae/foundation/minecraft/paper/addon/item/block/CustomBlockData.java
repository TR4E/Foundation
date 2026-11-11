package me.trae.foundation.minecraft.paper.addon.item.block;

import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.utilities.UtilTime;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

@Getter
@Setter
public class CustomBlockData {

    private static final NamespacedKey PLACED_AT_KEY = new NamespacedKey("custom_block_data", "placed_at");
    private static final NamespacedKey ITEM_UNIQUE_ID_KEY = new NamespacedKey("custom_block_data", "item_unique_id");
    private static final NamespacedKey PLACED_BY_KEY = new NamespacedKey("custom_block_data", "placed_by");
    private static final NamespacedKey EXPIRY_KEY = new NamespacedKey("custom_block_data", "expiry");
    private static final NamespacedKey LAST_TICKED_AT_KEY = new NamespacedKey("custom_block_data", "last_ticked_at");
    private static final NamespacedKey LAST_DAMAGED_AT_KEY = new NamespacedKey("custom_block_data", "last_damaged_at");

    private final long placedAt;
    private final String itemUniqueId;
    private final UUID placedBy;

    private long expiry, lastTickedAt, lastDamagedAt;

    private boolean dirty;

    public CustomBlockData(final CustomItem item, final Player player) {
        this.placedAt = System.currentTimeMillis();
        this.itemUniqueId = item.getUniqueId();
        this.placedBy = player != null ? player.getUniqueId() : null;
    }

    public CustomBlockData(final PersistentDataContainer persistentDataContainer) {
        this.placedAt = persistentDataContainer.get(PLACED_AT_KEY, PersistentDataType.LONG);
        this.itemUniqueId = persistentDataContainer.get(ITEM_UNIQUE_ID_KEY, PersistentDataType.STRING);
        this.placedBy = Optional.ofNullable(persistentDataContainer.get(PLACED_BY_KEY, PersistentDataType.STRING)).map(UUID::fromString).orElse(null);
        this.expiry = persistentDataContainer.get(EXPIRY_KEY, PersistentDataType.LONG);
        this.lastTickedAt = persistentDataContainer.get(LAST_TICKED_AT_KEY, PersistentDataType.LONG);
        this.lastDamagedAt = persistentDataContainer.get(LAST_DAMAGED_AT_KEY, PersistentDataType.LONG);
    }

    public static Optional<String> getItemUniqueId(final PersistentDataContainer persistentDataContainer) {
        return Optional.ofNullable(persistentDataContainer.get(ITEM_UNIQUE_ID_KEY, PersistentDataType.STRING));
    }

    public final boolean hasExpired() {
        if (this.expiry <= 0L) {
            return false;
        }

        return UtilTime.elapsed(this.getExpiryStartTime(), this.expiry);
    }

    public long getExpiryStartTime() {
        return this.placedAt;
    }

    public void write(final PersistentDataContainer persistentDataContainer) {
        persistentDataContainer.set(PLACED_AT_KEY, PersistentDataType.LONG, this.placedAt);
        persistentDataContainer.set(ITEM_UNIQUE_ID_KEY, PersistentDataType.STRING, this.itemUniqueId);

        if (this.placedBy != null) {
            persistentDataContainer.set(PLACED_BY_KEY, PersistentDataType.STRING, this.placedBy.toString());
        }

        persistentDataContainer.set(EXPIRY_KEY, PersistentDataType.LONG, this.expiry);
        persistentDataContainer.set(LAST_TICKED_AT_KEY, PersistentDataType.LONG, this.lastTickedAt);
        persistentDataContainer.set(LAST_DAMAGED_AT_KEY, PersistentDataType.LONG, this.lastDamagedAt);
    }
}