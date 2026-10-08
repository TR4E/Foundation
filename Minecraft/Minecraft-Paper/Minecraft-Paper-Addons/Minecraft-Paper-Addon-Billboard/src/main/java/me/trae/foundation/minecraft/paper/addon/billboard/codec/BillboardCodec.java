package me.trae.foundation.minecraft.paper.addon.billboard.codec;

import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.paper.addon.billboard.patch.BillboardPatch;
import me.trae.foundation.minecraft.paper.addon.billboard.types.BillboardVideo;
import me.trae.foundation.minecraft.paper.addon.billboard.utility.UtilMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

@CustomLog
@UtilityClass
public class BillboardCodec {

    private static final String CACHE_FILE_NAME = ".cache";

    private static final int MAGIC = 0x42424430;

    private static final int VERSION = 1;

    public static void load(final BillboardVideo billboardVideo) throws IOException {
        final List<File> frameFiles = billboardVideo.getFrameFiles();
        final long key = getKey(billboardVideo, frameFiles);
        final File cacheFile = new File(billboardVideo.getDirectory(), CACHE_FILE_NAME);

        if (cacheFile.isFile() && read(billboardVideo, cacheFile, key)) {
            return;
        }

        encode(billboardVideo, frameFiles);

        try {
            write(billboardVideo, cacheFile, key);
        } catch (final IOException exception) {
            LOGGER.warn("Failed to write cache for billboard {}", billboardVideo.getIdentifier(), exception);
        }
    }

    private static long getKey(final BillboardVideo video, final List<File> frameFiles) {
        long key = VERSION;
        key = 31L * key + video.getColumns();
        key = 31L * key + video.getRows();

        for (final File file : frameFiles) {
            key = 31L * key + file.getName().hashCode();
            key = 31L * key + file.length();
            key = 31L * key + file.lastModified();
        }

        return key;
    }

    private static void encode(final BillboardVideo video, final List<File> frameFiles) throws IOException {
        final List<BillboardPatch[]> patchFrameList = new ArrayList<>(frameFiles.size());

        byte[][] first = null;
        byte[][] previous = null;

        for (final File file : frameFiles) {
            final BufferedImage image = ImageIO.read(file);
            if (image == null) {
                throw new IOException("Unsupported image format: %s".formatted(file.getName()));
            }

            final byte[][] tiles = UtilMap.toTiles(image, video.getColumns(), video.getRows());

            patchFrameList.add(previous == null ? null : diff(previous, tiles));

            if (first == null) {
                first = tiles;
            }

            previous = tiles;
        }

        patchFrameList.set(0, diff(previous, first));

        video.install(first, patchFrameList);
    }

    private static BillboardPatch[] diff(final byte[][] previous, final byte[][] current) {
        final List<BillboardPatch> patchList = new ArrayList<>();

        for (int tile = 0; tile < current.length; tile++) {
            int minX = UtilMap.MAP_SIZE, minY = UtilMap.MAP_SIZE, maxX = -1, maxY = -1;

            for (int y = 0; y < UtilMap.MAP_SIZE; y++) {
                for (int x = 0; x < UtilMap.MAP_SIZE; x++) {
                    final int index = y * UtilMap.MAP_SIZE + x;
                    if (previous[tile][index] != current[tile][index]) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }

            if (maxX < 0) {
                continue;
            }

            final int patchWidth = maxX - minX + 1;
            final int patchHeight = maxY - minY + 1;
            final byte[] colors = new byte[patchWidth * patchHeight];

            for (int row = 0; row < patchHeight; row++) {
                System.arraycopy(current[tile], (minY + row) * UtilMap.MAP_SIZE + minX, colors, row * patchWidth, patchWidth);
            }

            patchList.add(new BillboardPatch(tile, minX, minY, patchWidth, patchHeight, colors));
        }

        return patchList.toArray(new BillboardPatch[0]);
    }

    private static void write(final BillboardVideo video, final File file, final long key) throws IOException {
        try (final DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(new FileOutputStream(file))))) {
            output.writeInt(MAGIC);
            output.writeLong(key);
            output.writeInt(video.getCanvas().length);

            for (final byte[] tile : video.getCanvas()) {
                output.write(tile);
            }

            output.writeInt(video.getPatchFrameList().size());

            for (final BillboardPatch[] patches : video.getPatchFrameList()) {
                output.writeInt(patches.length);

                for (final BillboardPatch patch : patches) {
                    output.writeShort(patch.tile());
                    output.writeByte(patch.x());
                    output.writeByte(patch.y());
                    output.writeByte(patch.width());
                    output.writeByte(patch.height());
                    output.write(patch.colors());
                }
            }
        }
    }

    private static boolean read(final BillboardVideo video, final File file, final long key) {
        try (final DataInputStream input = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(file))))) {
            if (input.readInt() != MAGIC || input.readLong() != key || input.readInt() != video.getTileCount()) {
                return false;
            }

            final byte[][] canvas = new byte[video.getTileCount()][UtilMap.MAP_SIZE * UtilMap.MAP_SIZE];

            for (final byte[] tile : canvas) {
                input.readFully(tile);
            }

            final int frameCount = input.readInt();
            final List<BillboardPatch[]> patchFrameList = new ArrayList<>(frameCount);

            for (int frame = 0; frame < frameCount; frame++) {
                final BillboardPatch[] patches = new BillboardPatch[input.readInt()];

                for (int index = 0; index < patches.length; index++) {
                    final int tile = input.readUnsignedShort();
                    final int x = input.readUnsignedByte();
                    final int y = input.readUnsignedByte();
                    final int width = input.readUnsignedByte();
                    final int height = input.readUnsignedByte();

                    final byte[] colors = new byte[width * height];
                    input.readFully(colors);

                    patches[index] = new BillboardPatch(tile, x, y, width, height, colors);
                }

                patchFrameList.add(patches);
            }

            video.install(canvas, patchFrameList);

            return true;
        } catch (final IOException exception) {
            return false;
        }
    }
}