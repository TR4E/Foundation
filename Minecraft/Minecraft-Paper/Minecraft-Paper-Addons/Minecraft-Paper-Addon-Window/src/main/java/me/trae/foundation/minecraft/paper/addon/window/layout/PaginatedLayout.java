package me.trae.foundation.minecraft.paper.addon.window.layout;

import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.window.constants.WindowConstants;
import me.trae.foundation.minecraft.paper.addon.window.element.Element;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
public final class PaginatedLayout extends Layout {

    private final int[] contentSlots;

    private List<Element<?>> content;
    private int total, page;

    PaginatedLayout(final int size, final int[] contentSlots) {
        super(size);

        if (contentSlots.length == 0) {
            throw new IllegalArgumentException("%s requires at least one content slot".formatted(PaginatedLayout.class.getSimpleName()));
        }

        for (final int slot : contentSlots) {
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("%s content slots must fall within the layout size".formatted(PaginatedLayout.class.getSimpleName()));
            }
        }

        this.contentSlots = contentSlots;
        this.content = new ArrayList<>();
        this.total = 0;
        this.page = 0;
    }

    public static PaginatedLayout of(final int size, final int[] contentSlots) {
        return new PaginatedLayout(size, contentSlots);
    }

    public static PaginatedLayout createDoubleChest(final int[] contentSlots) {
        return new PaginatedLayout(WindowConstants.DOUBLE_CHEST_WINDOW_SIZE, contentSlots);
    }

    public static PaginatedLayout createSingleChest(final int[] contentSlots) {
        return new PaginatedLayout(WindowConstants.SINGLE_CHEST_WINDOW_SIZE, contentSlots);
    }

    public static PaginatedLayout createDispenser(final int[] contentSlots) {
        return new PaginatedLayout(WindowConstants.DISPENSER_WINDOW_SIZE, contentSlots);
    }

    public static PaginatedLayout createHopper(final int[] contentSlots) {
        return new PaginatedLayout(WindowConstants.HOPPER_WINDOW_SIZE, contentSlots);
    }

    public <Type extends Element<?>> void setContent(final List<Type> content) {
        this.content = new ArrayList<>(content);
    }

    public void setTotal(final int total) {
        this.total = Math.max(0, total);
        this.page = Math.clamp(this.page, 0, this.getPageCount() - 1);
    }

    public void setPage(final int page) {
        this.page = Math.clamp(page, 0, this.getPageCount() - 1);
    }

    public int getOffset() {
        return this.page * this.contentSlots.length;
    }

    public int getLimit() {
        return this.contentSlots.length;
    }

    @Override
    public void clear() {
        super.clear();

        this.content = new ArrayList<>();
    }

    @Override
    public Optional<Element<?>> getElementBySlot(final int slot) {
        final int index = this.getContentIndex(slot);

        if (index == -1 || index >= this.content.size()) {
            return super.getElementBySlot(slot);
        }

        return Optional.of(this.content.get(index));
    }

    @Override
    public void applyTo(final Inventory inventory) {
        super.applyTo(inventory);

        for (int index = 0; index < this.contentSlots.length; index++) {
            if (index >= this.content.size()) {
                break;
            }

            inventory.setItem(this.contentSlots[index], this.content.get(index).toItemStack());
        }
    }

    public int getPageCount() {
        return Math.max(1, (int) (((long) this.total + this.contentSlots.length - 1) / this.contentSlots.length));
    }

    public boolean hasNextPage() {
        return this.page + 1 < this.getPageCount();
    }

    public boolean hasPreviousPage() {
        return this.page > 0;
    }

    public boolean nextPage() {
        if (!this.hasNextPage()) {
            return false;
        }

        this.page++;

        return true;
    }

    public boolean previousPage() {
        if (!this.hasPreviousPage()) {
            return false;
        }

        this.page--;

        return true;
    }

    private int getContentIndex(final int slot) {
        for (int index = 0; index < this.contentSlots.length; index++) {
            if (this.contentSlots[index] != slot) {
                continue;
            }

            return index;
        }

        return -1;
    }
}