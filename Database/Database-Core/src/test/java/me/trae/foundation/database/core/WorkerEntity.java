package me.trae.foundation.database.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;

import java.util.UUID;

@RequiredArgsConstructor
@Getter
@Setter
public final class WorkerEntity implements Entity {

    private final UUID id;
    private String value;
}