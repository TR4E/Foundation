package me.trae.foundation.minecraft.paper.addon.hologram;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilMessage;
import me.trae.foundation.utilities.UtilJava;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Getter
public abstract class Hologram {

    private final Set<UUID> viewerSet = ConcurrentHashMap.newKeySet();

    private final String name;
    private final Location location;

    private net.minecraft.world.entity.Display.TextDisplay handle;
    private org.bukkit.entity.TextDisplay textDisplay;

    public final boolean isBuilt() {
        return this.handle != null;
    }

    public final int getEntityId() {
        return this.handle.getId();
    }

    public final UUID getEntityUuid() {
        return this.handle.getUUID();
    }

    public final Component getComponent(final Player player) {
        return Component.join(JoinConfiguration.newlines(), this.getLines(player).stream().map(UtilMessage::deserialize).toList());
    }

    public abstract List<String> getLines(final Player player);

    public boolean canSee(final Player player) {
        return true;
    }

    public Billboard getBillboard() {
        return Billboard.CENTER;
    }

    public TextAlignment getAlignment() {
        return TextAlignment.CENTER;
    }

    public Color getBackgroundColor() {
        return Color.fromARGB(0, 0, 0, 0);
    }

    public float getScale() {
        return 1.0F;
    }

    public byte getTextOpacity() {
        return -1;
    }

    public int getLineWidth() {
        return 200;
    }

    public boolean isSeeThrough() {
        return false;
    }

    public boolean isShadowed() {
        return false;
    }

    public double getViewDistance() {
        return 48.0D;
    }

    public boolean isDynamic() {
        return false;
    }

    public final boolean isVisible(final Player player, final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (!location.getWorld().equals(player.getWorld())) {
            return false;
        }

        final double x = location.getX() - player.getX();
        final double y = location.getY() - player.getY();
        final double z = location.getZ() - player.getZ();

        if ((x * x) + (y * y) + (z * z) > this.getViewDistance() * this.getViewDistance()) {
            return false;
        }

        return this.canSee(player);
    }

    public final boolean isVisible(final Player player) {
        return this.isVisible(player, this.getLocation());
    }

    public final void build() {
        final Location location = this.getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }

        final ServerLevel serverLevel = UtilJava.cast(CraftWorld.class, location.getWorld()).getHandle();

        if (this.handle == null || this.textDisplay == null || !this.handle.level().equals(serverLevel)) {
            this.handle = new net.minecraft.world.entity.Display.TextDisplay(EntityType.TEXT_DISPLAY, serverLevel);
            this.textDisplay = UtilJava.cast(org.bukkit.entity.TextDisplay.class, this.handle.getBukkitEntity());
        }

        this.handle.setPos(location.getX(), location.getY(), location.getZ());
        this.handle.setYRot(location.getYaw());
        this.handle.setXRot(location.getPitch());

        this.textDisplay.setBillboard(this.getBillboard());
        this.textDisplay.setAlignment(this.getAlignment());
        this.textDisplay.setBackgroundColor(this.getBackgroundColor());
        this.textDisplay.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(this.getScale(), this.getScale(), this.getScale()), new Quaternionf()));
        this.textDisplay.setTextOpacity(this.getTextOpacity());
        this.textDisplay.setLineWidth(this.getLineWidth());
        this.textDisplay.setSeeThrough(this.isSeeThrough());
        this.textDisplay.setShadowed(this.isShadowed());
        this.textDisplay.setDefaultBackground(false);
        this.textDisplay.setViewRange((float) (this.getViewDistance() / 64.0D));
    }
}