package me.trae.foundation.database.api.exception;

import lombok.experimental.StandardException;

/**
 * A write succeeded, but one or more cache owners could not confirm synchronization.
 */
@StandardException
public final class SynchronizationException extends DatabaseException {
}