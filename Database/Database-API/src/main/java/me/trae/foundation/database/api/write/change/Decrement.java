package me.trae.foundation.database.api.write.change;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public final class Decrement {

    public static final long REFUSED = -2L;
    public static final long UNSUPPORTED = -3L;

    private final long delta, floor;
}