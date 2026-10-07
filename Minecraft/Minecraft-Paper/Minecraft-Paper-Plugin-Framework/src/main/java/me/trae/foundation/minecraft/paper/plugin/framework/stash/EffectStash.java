package me.trae.foundation.minecraft.paper.plugin.framework.stash;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class EffectStash {

    private final Effect effect;
    private final Object data;

    public static EffectStash of(final Effect effect, final Object data) {
        final boolean valid = effect != null && (data == null ? effect.getData() == null : effect.getData() != null && effect.getData().isInstance(data));

        return new EffectStash(valid ? effect : null, data);
    }

    public static EffectStash of(final Effect effect) {
        return of(effect, null);
    }

    public void play(final Location location) {
        if (this.effect == null) {
            return;
        }

        location.getWorld().playEffect(location, this.effect, this.data);
    }

    public void play(final Player player, final Location location) {
        if (this.effect == null) {
            return;
        }

        player.playEffect(location, this.effect, this.data);
    }

    public void play(final Player player) {
        this.play(player, player.getLocation());
    }

    public void broadcast() {
        if (this.effect == null) {
            return;
        }

        UtilServer.getOnlinePlayers().forEach(this::play);
    }
}