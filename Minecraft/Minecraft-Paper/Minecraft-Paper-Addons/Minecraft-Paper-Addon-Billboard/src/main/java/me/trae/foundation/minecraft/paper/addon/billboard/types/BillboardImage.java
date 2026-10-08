package me.trae.foundation.minecraft.paper.addon.billboard.types;

import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.billboard.utility.UtilMap;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public abstract non-sealed class BillboardImage extends Billboard {

    private final Set<UUID> sentSet = ConcurrentHashMap.newKeySet();

    private byte[][] tiles;

    public BillboardImage(final String identifier, final Location location, final BlockFace facing, final int columns, final int rows) {
        super(identifier, location, facing, columns, rows);
    }

    protected abstract BufferedImage loadImage() throws IOException;

    @Override
    public void load() throws IOException {
        final BufferedImage image = this.loadImage();
        if (image == null) {
            throw new IOException("Unsupported image format");
        }

        this.tiles = UtilMap.toTiles(image, this.getColumns(), this.getRows());
    }
}