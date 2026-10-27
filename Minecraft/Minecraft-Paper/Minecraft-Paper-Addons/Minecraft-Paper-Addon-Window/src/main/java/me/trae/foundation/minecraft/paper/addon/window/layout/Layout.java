package me.trae.foundation.minecraft.paper.addon.window.layout;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.window.element.Element;
import org.bukkit.inventory.Inventory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Layout {

    private final ConcurrentHashMap<Integer, Element<?>> elementMap = new ConcurrentHashMap<>();

    private final int size;

    public static Layout of(final int size) {
        return new Layout(size);
    }

    public final void set(final int slot, final Element<?> element) {
        if (slot < 0 || slot >= this.size) {
            return;
        }

        this.elementMap.put(slot, element);
    }

    public final void fill(final Element<?> element) {
        for (int slot = 0; slot < this.size; slot++) {
            this.elementMap.putIfAbsent(slot, element);
        }
    }

    public final void border(final Element<?> element) {
        for (int slot = 0; slot < this.size; slot++) {
            if (slot > 8 && slot < this.size - 9 && slot % 9 != 0 && slot % 9 != 8) {
                continue;
            }

            this.elementMap.put(slot, element);
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

    public final void clear() {
        this.elementMap.clear();
    }

    public Optional<Element<?>> getElementBySlot(final int slot) {
        return Optional.ofNullable(this.elementMap.get(slot));
    }

    public void applyTo(final Inventory inventory) {
        inventory.clear();

        this.elementMap.forEach((slot, element) -> inventory.setItem(slot, element.toItemStack()));
    }
}