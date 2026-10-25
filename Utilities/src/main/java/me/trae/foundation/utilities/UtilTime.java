package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;
import me.trae.foundation.utilities.enums.TimeUnit;
import me.trae.foundation.utilities.enums.UnitFormat;

import java.util.Locale;
import java.util.function.Function;

@UtilityClass
public class UtilTime {

    public static String getTime(final long duration, TimeUnit timeUnit, final int trim, final UnitFormat unitFormat, final boolean useMilliseconds, final Function<String, String> function) {
        if (!useMilliseconds && timeUnit == TimeUnit.MILLISECONDS) {
            timeUnit = TimeUnit.SECONDS;
        }

        final double value = (double) duration / timeUnit.getDuration();

        final long rounded = Math.round(value);

        final String label = switch (unitFormat) {
            case NORMALIZED -> timeUnit.label(value);
            case LOWERCASE -> timeUnit.label(value).toLowerCase(Locale.ROOT);
            case SHORT -> timeUnit.getShortName();
        };

        final String durationString = trim == 0 ? String.valueOf(rounded) : "%s." + trim + "f %s".formatted(value);

        return "%s %s".formatted(function.apply(durationString), label);
    }

    public static String getTime(final long duration, final TimeUnit timeUnit, final int trim, final UnitFormat unitFormat, final boolean useMilliseconds) {
        return getTime(duration, timeUnit, trim, unitFormat, useMilliseconds, Function.identity());
    }

    public static String getBestTime(final long duration, final UnitFormat unitFormat, final int trim, final boolean useMilliseconds, final Function<String, String> function) {
        return getTime(duration, TimeUnit.getByDuration(duration), trim, unitFormat, useMilliseconds, function);
    }

    public static String getBestTime(final long duration, final UnitFormat unitFormat, final int trim, final boolean useMilliseconds) {
        return getTime(duration, TimeUnit.getByDuration(duration), trim, unitFormat, useMilliseconds);
    }

    public static String getReadableTime(final long duration, final TimeUnit timeUnit, final int trim, final Function<String, String> function) {
        return getTime(duration, timeUnit, trim, UnitFormat.NORMALIZED, false, function);
    }

    public static String getReadableTime(final long duration, final TimeUnit timeUnit, final int trim) {
        return getTime(duration, timeUnit, trim, UnitFormat.NORMALIZED, false);
    }

    public static String getReadableShortTime(final long duration, final TimeUnit timeUnit, final int trim, final Function<String, String> function) {
        return getTime(duration, timeUnit, trim, UnitFormat.SHORT, false, function);
    }

    public static String getReadableShortTime(final long duration, final TimeUnit timeUnit, final int trim) {
        return getTime(duration, timeUnit, trim, UnitFormat.SHORT, false);
    }

    public static boolean elapsed(final long systemTime, final long duration) {
        // Permanent
        if (systemTime == -1L || duration == -1L) {
            return false;
        }

        // Instant
        if (systemTime == 0L | duration == 0L) {
            return true;
        }

        return System.currentTimeMillis() - systemTime >= duration;
    }

    public static boolean elapsed(final long systemTime) {
        if (systemTime == -1L) {
            return false;
        }

        if (systemTime == 0L) {
            return true;
        }

        return System.currentTimeMillis() >= systemTime;
    }

    public static long getRemaining(final long systemTime, final long duration) {
        return (systemTime + duration) - System.currentTimeMillis();
    }
}