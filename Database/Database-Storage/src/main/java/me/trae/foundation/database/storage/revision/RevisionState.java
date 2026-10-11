package me.trae.foundation.database.storage.revision;

public final class RevisionState {

    private volatile long revision;

    public long getRevision() {
        return this.revision;
    }

    public void setRevision(final long revision) {
        this.revision = revision;
    }
}
