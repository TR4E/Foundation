package me.trae.foundation.minecraft.paper.addon.billboard.patch;

import me.trae.foundation.minecraft.paper.addon.billboard.utility.UtilMap;

public record BillboardPatch(int tile, int x, int y, int width, int height, byte[] colors) {

    public void apply(final byte[][] canvas) {
        for (int row = 0; row < this.height; row++) {
            System.arraycopy(this.colors, row * this.width, canvas[this.tile], (this.y + row) * UtilMap.MAP_SIZE + this.x, this.width);
        }
    }
}