package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;
import me.trae.foundation.utilities.enums.TimeUnit;
import me.trae.foundation.utilities.enums.UnitFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
            case NORMALIZED -> " " + timeUnit.label(value);
            case LOWERCASE -> " " + timeUnit.label(value).toLowerCase(Locale.ROOT);
            case SHORT -> timeUnit.getShortName();
        };

        final String durationString = trim == 0 ? String.valueOf(rounded) : ("%." + trim + "f").formatted(value);

        return function.apply(durationString) + label;
    }

    public static String getTime(final long duration, final TimeUnit timeUnit, final int trim, final UnitFormat unitFormat, final boolean useMilliseconds) {
        return getTime(duration, timeUnit, trim, unitFormat, useMilliseconds, Function.identity());
    }

    public static String getBestTime(final long duration, final int trim, final UnitFormat unitFormat, final boolean useMilliseconds, final Function<String, String> function) {
        return getTime(duration, TimeUnit.getByDuration(duration), trim, unitFormat, useMilliseconds, function);
    }

    public static String getBestTime(final long duration, final int trim, final UnitFormat unitFormat, final boolean useMilliseconds) {
        return getTime(duration, TimeUnit.getByDuration(duration), trim, unitFormat, useMilliseconds);
    }

    public static String getReadableTime(final long duration, final TimeUnit timeUnit, final int trim, final Function<String, String> function) {
        return getTime(duration, timeUnit, trim, UnitFormat.NORMALIZED, false, function);
    }

    public static String getReadableTime(final long duration, final TimeUnit timeUnit, final int trim) {
        return getTime(duration, timeUnit, trim, UnitFormat.NORMALIZED, false);
    }

    public static String getBestReadableTime(final long duration, final int trim, final Function<String, String> function) {
        return getBestTime(duration, trim, UnitFormat.NORMALIZED, false, function);
    }

    public static String getBestReadableTime(final long duration, final int trim) {
        return getBestTime(duration, trim, UnitFormat.NORMALIZED, false);
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

    public static Optional<Long> parseByInput(final String input) {
        if (input == null) {
            return Optional.empty();
        }

        final String trimmed = input.trim().toLowerCase(Locale.ROOT);

        for (final TimeUnit timeUnit : TimeUnit.values()) {
            if (!trimmed.endsWith(timeUnit.getShortName())) {
                continue;
            }

            try {
                final long value = Long.parseLong(trimmed.substring(0, trimmed.length() - timeUnit.getShortName().length()));

                return Optional.of(value * timeUnit.getDuration());
            } catch (final Exception ignored) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    public static String getMultiTime(final long duration, final List<TimeUnit> timeUnitList, final int trim, final UnitFormat unitFormat, final Function<String, String> function, final String join) {
        final List<TimeUnit> sortedTimeUnitList = timeUnitList.stream()
                .sorted(Comparator.comparingLong(TimeUnit::getDuration).reversed())
                .toList();

        final List<String> partList = new ArrayList<>();

        long remaining = duration;

        for (final TimeUnit timeUnit : sortedTimeUnitList) {
            final long amount = remaining / timeUnit.getDuration();

            if (amount <= 0L) {
                continue;
            }

            final long consumed = amount * timeUnit.getDuration();

            partList.add(function.apply(getTime(consumed, timeUnit, trim, unitFormat, true, Function.identity())));

            remaining -= consumed;
        }

        return String.join(join, partList);
    }
}