package me.trae.foundation.minecraft.paper.addon.billboard.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.utilities.UtilJava;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftWorld;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Getter
public abstract sealed class Billboard permits BillboardImage, BillboardVideo {

    private static final AtomicInteger MAP_ID_COUNTER = new AtomicInteger(Integer.MAX_VALUE);

    private final Set<UUID> viewerSet = ConcurrentHashMap.newKeySet();

    private final String identifier;

    private final Location location;

    private final BlockFace facing;

    private final int columns, rows;

    private int[] mapIds;

    private List<Packet<?>> spawnPacketList;

    private ClientboundRemoveEntitiesPacket removePacket;

    private Location center;

    private volatile boolean loaded;

    protected boolean isGlowing() {
        return true;
    }

    public double getViewDistance() {
        return 48.0D;
    }

    public final int getTileCount() {
        return this.columns * this.rows;
    }

    public final Block getBlock(final int index) {
        return this.location.getBlock().getRelative(this.getRight(), index % this.columns).getRelative(BlockFace.DOWN, index / this.columns);
    }

    public abstract void load() throws IOException;

    public boolean build() {
        final World world = this.location.getWorld();
        if (world == null) {
            return false;
        }

        final ServerLevel serverLevel = UtilJava.cast(CraftWorld.class, world).getHandle();
        final Direction direction = Direction.valueOf(this.facing.name());

        final int tileCount = this.getTileCount();
        final int[] mapIds = new int[tileCount];
        final int[] entityIds = new int[tileCount];
        final List<Packet<?>> spawnPacketList = new ArrayList<>(tileCount * 2);

        for (int index = 0; index < tileCount; index++) {
            final Block block = this.getBlock(index);
            final BlockPos blockPos = new BlockPos(block.getX(), block.getY(), block.getZ());

            final ItemStack itemStack = new ItemStack(Items.FILLED_MAP);
            mapIds[index] = MAP_ID_COUNTER.getAndDecrement();
            itemStack.set(DataComponents.MAP_ID, new MapId(mapIds[index]));

            final ItemFrame itemFrame = this.isGlowing() ? new GlowItemFrame(serverLevel, blockPos, direction) : new ItemFrame(serverLevel, blockPos, direction);
            itemFrame.setItem(itemStack, false, false);
            itemFrame.setInvisible(true);

            spawnPacketList.add(new ClientboundAddEntityPacket(itemFrame, direction.get3DDataValue(), blockPos));
            spawnPacketList.add(new ClientboundSetEntityDataPacket(itemFrame.getId(), itemFrame.getEntityData().getNonDefaultValues()));

            entityIds[index] = itemFrame.getId();
        }

        this.mapIds = mapIds;
        this.spawnPacketList = spawnPacketList;
        this.removePacket = new ClientboundRemoveEntitiesPacket(entityIds);
        this.center = this.getBlock((this.rows / 2) * this.columns + this.columns / 2).getLocation().add(0.5D, 0.5D, 0.5D);

        return true;
    }

    public void markLoaded() {
        this.loaded = true;
    }

    private BlockFace getRight() {
        return switch (this.facing) {
            case NORTH -> BlockFace.WEST;
            case EAST -> BlockFace.NORTH;
            case SOUTH -> BlockFace.EAST;
            case WEST -> BlockFace.SOUTH;
            default -> throw new IllegalArgumentException("Unsupported facing: %s".formatted(this.facing));
        };
    }
}