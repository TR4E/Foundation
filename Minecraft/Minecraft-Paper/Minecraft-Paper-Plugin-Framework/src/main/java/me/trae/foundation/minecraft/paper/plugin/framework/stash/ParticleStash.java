package me.trae.foundation.minecraft.paper.plugin.framework.stash;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.entity.Player;

import java.util.Optional;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class ParticleStash {

    private final String key;
    private final int count;
    private final double offsetX, offsetY, offsetZ, extra;
    private final Object data;

    public static ParticleStash of(final String key, final int count, final double offsetX, final double offsetY, final double offsetZ, final double extra, final Object data) {
        return new ParticleStash(key, count, offsetX, offsetY, offsetZ, extra, data);
    }

    public static ParticleStash of(final String key, final int count, final double offsetX, final double offsetY, final double offsetZ, final double extra) {
        return of(key, count, offsetX, offsetY, offsetZ, extra, null);
    }

    public static ParticleStash of(final String key, final int count) {
        return of(key, count, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    public static ParticleStash of(final Particle particle, final int count, final double offsetX, final double offsetY, final double offsetZ, final double extra, final Object data) {
        final NamespacedKey namespacedKey = particle != null ? Registry.PARTICLE_TYPE.getKey(particle) : null;

        return new ParticleStash(namespacedKey != null ? namespacedKey.asString() : null, count, offsetX, offsetY, offsetZ, extra, data);
    }

    public static ParticleStash of(final Particle particle, final int count, final double offsetX, final double offsetY, final double offsetZ, final double extra) {
        return of(particle, count, offsetX, offsetY, offsetZ, extra, null);
    }

    public static ParticleStash of(final Particle particle, final int count) {
        return of(particle, count, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    public Optional<Particle> getParticle() {
        return Optional.ofNullable(this.key)
                .map(NamespacedKey::fromString)
                .map(Registry.PARTICLE_TYPE::get);
    }

    public void play(final Location location) {
        this.getSpawnableParticle().ifPresent(particle -> location.getWorld().spawnParticle(particle, location, this.count, this.offsetX, this.offsetY, this.offsetZ, this.extra, this.data));
    }

    public void play(final Player player, final Location location) {
        this.getSpawnableParticle().ifPresent(particle -> player.spawnParticle(particle, location, this.count, this.offsetX, this.offsetY, this.offsetZ, this.extra, this.data));
    }

    public void play(final Player player) {
        this.play(player, player.getLocation());
    }

    public void broadcast() {
        if (this.getSpawnableParticle().isEmpty()) {
            return;
        }

        UtilServer.getOnlinePlayers().forEach(this::play);
    }

    private Optional<Particle> getSpawnableParticle() {
        return this.getParticle().filter(particle -> this.data == null ? particle.getDataType() == Void.class : particle.getDataType().isInstance(this.data));
    }
}