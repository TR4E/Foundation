package me.trae.foundation.minecraft.paper.addon.billboard.types;

import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.billboard.codec.BillboardCodec;
import me.trae.foundation.minecraft.paper.addon.billboard.patch.BillboardPatch;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Getter
public abstract non-sealed class BillboardVideo extends Billboard {

    private static final List<String> FRAME_EXTENSION_LIST = List.of(".png", ".jpg", ".jpeg");

    private byte[][] canvas;

    private List<BillboardPatch[]> patchFrameList;

    @Setter
    private int frameIndex;

    @Setter
    private long nextFrameTime;

    private volatile boolean playing;

    public BillboardVideo(final String identifier, final Location location, final BlockFace facing, final int columns, final int rows) {
        super(identifier, location, facing, columns, rows);
    }

    public abstract File getDirectory();

    public int getFrameRate() {
        return 10;
    }

    public boolean isLooping() {
        return true;
    }

    public boolean isAutoplay() {
        return true;
    }

    public void onLoop(final List<Player> viewerList) {
    }

    public final List<File> getFrameFiles() throws IOException {
        final File[] files = this.getDirectory().listFiles(file -> file.isFile() && FRAME_EXTENSION_LIST.stream().anyMatch(file.getName().toLowerCase(Locale.ROOT)::endsWith));
        if (files == null || files.length == 0) {
            throw new IOException("No frames in %s".formatted(this.getDirectory()));
        }

        return Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList();
    }

    @Override
    public void load() throws IOException {
        BillboardCodec.load(this);
    }

    public void install(final byte[][] canvas, final List<BillboardPatch[]> patchFrameList) {
        this.canvas = canvas;
        this.patchFrameList = patchFrameList;
        this.frameIndex = patchFrameList.size() - 1;
        this.nextFrameTime = System.nanoTime();
        this.playing = this.isAutoplay();
    }

    public void play() {
        if (!this.isLoaded()) {
            return;
        }

        this.nextFrameTime = System.nanoTime();
        this.playing = true;
    }

    public void stop() {
        this.playing = false;
    }
}