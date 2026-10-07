package me.trae.foundation.utilities.functional.consumer;

@FunctionalInterface
public interface TriConsumer<First, Second, Third> {

    void accept(final First first, final Second second, final Third third);
}