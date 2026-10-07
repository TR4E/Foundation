package me.trae.foundation.utilities.functional.consumer;

@FunctionalInterface
public interface QuadConsumer<First, Second, Third, Fourth> {

    void accept(final First first, final Second second, final Third third, final Fourth fourth);
}