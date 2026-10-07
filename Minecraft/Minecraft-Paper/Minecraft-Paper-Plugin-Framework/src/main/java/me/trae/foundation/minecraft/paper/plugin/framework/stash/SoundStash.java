package me.trae.foundation.minecraft.paper.plugin.framework.stash;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Optional;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class SoundStash {

    private final String key;
    private final SoundCategory category;
    private final float volume, pitch;

    public static SoundStash of(final String key, final SoundCategory category, final float volume, final float pitch) {
        return new SoundStash(key, category, volume, pitch);
    }

    public static SoundStash of(final String key, final SoundCategory category) {
        return of(key, category, 1.0F, 1.0F);
    }

    public static SoundStash of(final Sound sound, final SoundCategory category, final float volume, final float pitch) {
        final NamespacedKey namespacedKey = sound != null ? Registry.SOUNDS.getKey(sound) : null;

        return new SoundStash(namespacedKey != null ? namespacedKey.asString() : null, category, volume, pitch);
    }

    public static SoundStash of(final Sound sound, final SoundCategory category) {
        return of(sound, category, 1.0F, 1.0F);
    }

    public Optional<Sound> getSound() {
        return Optional.ofNullable(this.key)
                .map(NamespacedKey::fromString)
                .map(Registry.SOUNDS::get);
    }

    public void play(final Location location) {
        if (this.key == null || this.category == null) {
            return;
        }

        location.getWorld().playSound(location, this.key, this.category, this.volume, this.pitch);
    }

    public void play(final Player player) {
        if (this.key == null || this.category == null) {
            return;
        }

        player.playSound(player, this.key, this.category, this.volume, this.pitch);
    }

    public void broadcast() {
        if (this.key == null || this.category == null) {
            return;
        }

        UtilServer.getOnlinePlayers().forEach(this::play);
    }
}