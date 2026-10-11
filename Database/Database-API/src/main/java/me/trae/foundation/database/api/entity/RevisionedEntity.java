package me.trae.foundation.database.api.entity;

/**
 * An entity whose persisted revision is used to reject stale writes.
 * Entity identifiers must not be reused after deletion while stale instances may still exist.
 */
public interface RevisionedEntity extends Entity {

    long getRevision();

    void setRevision(final long revision);
}
