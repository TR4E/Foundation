package me.trae.foundation.minecraft.paper.addon.window.layout;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.window.constants.WindowConstants;
import me.trae.foundation.minecraft.paper.addon.window.element.Element;
import org.bukkit.inventory.Inventory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Layout {

    private final ConcurrentHashMap<Integer, Element<?>> elementMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Element<?>, Integer> slotMap = new ConcurrentHashMap<>();

    private final int size;

    public static Layout of(final int size) {
        return new Layout(size);
    }

    public static Layout createDoubleChest() {
        return of(WindowConstants.DOUBLE_CHEST_WINDOW_SIZE);
    }

    public static Layout createSingleChest() {
        return of(WindowConstants.SINGLE_CHEST_WINDOW_SIZE);
    }

    public static Layout createDispenser() {
        return of(WindowConstants.DISPENSER_WINDOW_SIZE);
    }

    public static Layout createHopper() {
        return of(WindowConstants.HOPPER_WINDOW_SIZE);
    }

    public final void set(final int slot, final Element<?> element) {
        if (slot < 0 || slot >= this.size) {
            return;
        }

        this.elementMap.put(slot, element);
        this.slotMap.put(element, slot);
    }

    public final void fill(final Element<?> element) {
        for (int slot = 0; slot < this.size; slot++) {
            if (this.elementMap.containsKey(slot)) {
                continue;
            }

            this.set(slot, element);
        }
    }

    public final void border(final Element<?> element) {
        for (int slot = 0; slot < this.size; slot++) {
            if (slot > 8 && slot < this.size - 9 && slot % 9 != 0 && slot % 9 != 8) {
                continue;
            }

            this.set(slot, element);
        }
    }

    public final void row(final int row, final Element<?> element) {
        for (int column = 0; column < 9; column++) {
            this.set(row * 9 + column, element);
        }
    }

    public final void column(final int column, final Element<?> element) {
        if (column < 0 || column > 8) {
            return;
        }

        for (int row = 0; row < this.size / 9; row++) {
            this.set(row * 9 + column, element);
        }
    }

    public void clear() {
        this.elementMap.clear();
        this.slotMap.clear();
    }

    public Optional<Element<?>> getElementBySlot(final int slot) {
        return Optional.ofNullable(this.elementMap.get(slot));
    }

    public Optional<Integer> getSlotByElement(final Element<?> element) {
        return Optional.ofNullable(this.slotMap.get(element));
    }

    public void applyTo(final Inventory inventory) {
        inventory.clear();

        this.elementMap.forEach((slot, element) -> inventory.setItem(slot, element.toItemStack()));
    }
}