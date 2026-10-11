package me.trae.foundation.database.storage.revision;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class RevisionState {

    private volatile long revision;
}