package me.trae.foundation.database.storage.revision;

import me.trae.foundation.database.api.entity.Entity;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public final class EntityRevisionStore {

    private final ReferenceQueue<Entity> referenceQueue = new ReferenceQueue<>();
    private final Map<IdentityReference, RevisionState> revisionMap = new HashMap<>();

    public RevisionState getState(final Entity entity) {
        synchronized (this.revisionMap) {
            this.removeCollectedEntities();

            final IdentityReference lookup = new IdentityReference(entity);
            RevisionState revisionState = this.revisionMap.get(lookup);

            if (revisionState == null) {
                revisionState = new RevisionState();
                this.revisionMap.put(new IdentityReference(entity, this.referenceQueue), revisionState);
            }

            return revisionState;
        }
    }

    public void setRevision(final Entity entity, final long revision) {
        this.getState(entity).setRevision(revision);
    }

    public boolean hasState(final Entity entity) {
        synchronized (this.revisionMap) {
            this.removeCollectedEntities();

            return this.revisionMap.containsKey(new IdentityReference(entity));
        }
    }

    private void removeCollectedEntities() {
        IdentityReference reference;

        while ((reference = (IdentityReference) this.referenceQueue.poll()) != null) {
            this.revisionMap.remove(reference);
        }
    }

    private static final class IdentityReference extends WeakReference<Entity> {

        private final int identityHashCode;

        private IdentityReference(final Entity entity) {
            super(entity);
            this.identityHashCode = System.identityHashCode(entity);
        }

        private IdentityReference(final Entity entity, final ReferenceQueue<Entity> referenceQueue) {
            super(entity, referenceQueue);
            this.identityHashCode = System.identityHashCode(entity);
        }

        @Override
        public int hashCode() {
            return this.identityHashCode;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof IdentityReference reference)) {
                return false;
            }

            final Entity entity = this.get();

            return entity != null && entity == reference.get();
        }
    }
}