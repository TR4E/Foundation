package me.trae.foundation.database.api.entity;

public interface RevisionedEntity extends Entity {

    long getRevision();

    void setRevision(final long revision);
}