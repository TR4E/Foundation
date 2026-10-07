package me.trae.foundation.utilities.functional.function;

@FunctionalInterface
public interface TriFunction<First, Second, Third, Return> {

    Return apply(final First first, final Second second, final Third third);
}