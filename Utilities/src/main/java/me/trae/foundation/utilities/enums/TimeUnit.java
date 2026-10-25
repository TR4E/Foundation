package me.trae.foundation.utilities.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.utilities.UtilString;

@AllArgsConstructor
@Getter
public enum TimeUnit {

    MILLISECONDS("ms", 1L),
    SECONDS("s", 1_000L),
    MINUTES("m", 60_000L),
    HOURS("h", 3_600_000L),
    DAYS("d", 86_400_000L),
    WEEKS("w", 604_800_000L),
    MONTHS("mo", 2_629_800_000L),
    YEARS("y", 31_557_600_000L);

    private final String name = UtilString.clean(this.name());

    private final String shortName;
    private final long duration;

    public static TimeUnit getByDuration(final long duration) {
        TimeUnit bestTimeUnit = MILLISECONDS;

        for (final TimeUnit timeUnit : values()) {
            if (duration < timeUnit.getDuration()) {
                continue;
            }

            bestTimeUnit = timeUnit;
        }

        return bestTimeUnit;
    }

    public final String label(final double value) {
        final String label = this.name().toLowerCase();

        return value == 1.0D ? label.substring(0, label.length() - 1) : label;
    }
}