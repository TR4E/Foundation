package me.trae.foundation.database.api.write;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public final class WriteResult {

    private final long version;
    private final Long value;
}