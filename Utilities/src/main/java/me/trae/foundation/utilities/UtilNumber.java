package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@UtilityClass
public class UtilNumber {

    private static final DecimalFormatSymbols DECIMAL_FORMAT_SYMBOLS_INSTANCE = DecimalFormatSymbols.getInstance(Locale.ROOT);

    public static String format(final String format, final Number input) {
        final DecimalFormat decimalFormat = new DecimalFormat(format, DECIMAL_FORMAT_SYMBOLS_INSTANCE);

        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);

        return decimalFormat.format(input);
    }

    public static <T extends Number & Comparable<T>> T clamp(final T minimum, final T maximum, final T value) {
        if (value.compareTo(minimum) < 0) {
            return minimum;
        }

        if (value.compareTo(maximum) > 0) {
            return maximum;
        }

        return value;
    }

    public static <T extends Number> T getRandomNumber(final Class<T> type, final T minimum, final T maximum) {
        if (minimum.equals(maximum)) {
            return minimum;
        }

        final ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();

        return type.cast(switch (minimum) {
            case final Integer value -> threadLocalRandom.nextInt(value, maximum.intValue());
            case final Long value -> threadLocalRandom.nextLong(value, maximum.longValue());
            case final Double value -> threadLocalRandom.nextDouble(value, maximum.doubleValue());
            case final Float value -> threadLocalRandom.nextFloat(value, maximum.floatValue());
            default -> throw new IllegalArgumentException("Unsupported numeric type: %s".formatted(type.getName()));
        });
    }
}