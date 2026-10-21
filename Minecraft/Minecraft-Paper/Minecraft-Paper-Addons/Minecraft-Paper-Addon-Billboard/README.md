<h1 align="center">Minecraft-Paper-Addon-Billboard</h1>

<p align="center">Render images and animated videos on maps using item frames.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper NMS" src="https://img.shields.io/badge/Paper-NMS-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Billboard addon displays images and animated videos on grids of item frames, each backed by a Minecraft map. It converts image pixels to the map color palette, distributes tiles across the grid, and manages per-player visibility with distance-based culling. Videos use delta encoding to send only changed pixels between frames, reducing bandwidth by roughly 90 percent compared to full-frame updates.

## Contents

<details>
<summary>On this page</summary>

- [Overview](#overview)
- [Image billboards](#image-billboards)
- [Video billboards](#video-billboards)
- [Visibility and culling](#visibility-and-culling)
- [Color matching](#color-matching)
- [Installation](#installation)

</details>

## Overview

`BillboardService` is the central manager. It holds all registered billboards, runs a 50-millisecond scheduler tick for video frame advancement and viewer reconciliation, and provides methods for creating and removing billboards. Billboards are defined as a sealed class hierarchy:

| Type | Purpose |
| --- | --- |
| `BillboardImage` | Static image display across a grid of maps |
| `BillboardVideo` | Animated display with delta-encoded frame sequences |

Both types are placed as item frames on a wall at a given location, facing a cardinal direction, spanning a configurable number of columns and rows. Each cell is a 128x128 map tile.

## Image billboards

An image billboard loads an image file, scales it to the grid dimensions (columns x rows x 128 pixels), converts each pixel to the closest Minecraft map color, and distributes the result across map items in item frames. The frames are spawned as entities at the computed world positions.

The color conversion uses `UtilMap`, which matches each pixel to the nearest entry in Minecraft's map color palette using CIE color distance. The result is a byte array per tile sent through `ClientboundMapItemDataPacket`.

```java
@Singleton
public class LogoBillboard extends BillboardImage {

    public LogoBillboard() {
        super("logo", new Location(world, 100, 65, 200), BlockFace.NORTH, 4, 3);
    }

    @Override
    protected BufferedImage loadImage() throws IOException {
        return ImageIO.read(new File("logo.png"));
    }

    @Override
    protected boolean isGlowing() {
        return true;
    }

    @Override
    public double getViewDistance() {
        return 64.0D;
    }
}
```

The constructor takes a unique identifier, the wall location, the facing direction, and the grid dimensions (columns x rows). Override `loadImage()` to provide the `BufferedImage` source. Override `isGlowing()` to toggle between glow item frames and regular item frames (default `true`). Override `getViewDistance()` to control how far away players can see the billboard (default `48.0`).

## Video billboards

A video billboard loads a sequence of image files as frames. The first frame is a full image. Subsequent frames are stored as delta patches: only the pixels that changed from the previous frame are recorded. The patches are compressed with DEFLATE and cached to a `.cache` file on disk. If the source frames have not changed, the cache is reused on the next load.

`BillboardCodec` handles compression and decompression. `BillboardPatch` is a record holding the tile index and the changed pixel data.

On each scheduler tick (50 milliseconds), the service advances the video frame using nanosecond-precision timing, applies the delta patches to the canvas, and sends `ClientboundMapItemDataPacket` updates to viewers. A loop callback fires when the video wraps around.

```java
@Singleton
public class IntroBillboard extends BillboardVideo {

    public IntroBillboard() {
        super("intro", new Location(world, 100, 65, 200), BlockFace.NORTH, 5, 3);
    }

    @Override
    public File getDirectory() {
        return new File("frames/intro");
    }

    @Override
    public int getFrameRate() {
        return 20;
    }

    @Override
    public boolean isLooping() {
        return true;
    }

    @Override
    public boolean isAutoplay() {
        return true;
    }

    @Override
    public void onLoop(final List<Player> viewerList) {
        // Called each time the video loops
    }
}
```

The constructor takes the same parameters as `BillboardImage`. Override `getDirectory()` to point to a folder of sequentially named frame images (`.png`, `.jpg`, `.jpeg`). Override `getFrameRate()` to control playback speed in frames per second (default `10`). Override `isLooping()` and `isAutoplay()` to control playback behavior.

Video playback can be controlled programmatically with `play()` and `stop()`.

## Visibility and culling

Billboards track which players can see them based on distance. The default view distance is 48 blocks, with an 8-block margin for hysteresis to prevent flickering at the boundary. The service reconciles viewer lists every 10 ticks (500 milliseconds).

When a player enters range, the billboard spawns item frame entities and sends map tile data. When a player leaves range, the entities are removed. Tile data is tracked per player so that only unsent or changed tiles are transmitted.

## Color matching

`UtilMap` builds a palette of all Minecraft map colors at startup. For each pixel, it finds the closest palette entry using CIE76 color distance in Lab color space, which produces more perceptually uniform results than RGB distance. The alpha channel is stripped before matching.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Billboard</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Requires Paper NMS. Return to the [Addons README](../README.md).
