package me.trae.foundation.minecraft.paper.addon.death.reason;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.utilities.UtilTime;
import net.kyori.adventure.text.Component;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class Reason {

    private final Component prefix, name, suffix;
    private final long systemTime, duration;

    public static Reason of(final Component prefix, final Component name, final Component suffix, final long duration) {
        return new Reason(prefix, name, suffix, System.currentTimeMillis(), duration);
    }

    public static Reason of(final Component name, final long duration) {
        return of(null, name, null, duration);
    }

    public final boolean hasExpired() {
        return UtilTime.elapsed(this.systemTime, this.duration);
    }
}