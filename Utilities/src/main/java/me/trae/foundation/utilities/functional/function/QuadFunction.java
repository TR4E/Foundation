package me.trae.foundation.utilities.functional.function;

@FunctionalInterface
public interface QuadFunction<First, Second, Third, Fourth, Return> {

    Return apply(final First first, final Second second, final Third third, final Fourth fourth);
}