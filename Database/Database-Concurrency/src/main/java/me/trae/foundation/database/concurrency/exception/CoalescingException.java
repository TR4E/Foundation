package me.trae.foundation.database.concurrency.exception;

import lombok.experimental.StandardException;
import me.trae.foundation.database.api.exception.DatabaseException;

@StandardException
public final class CoalescingException extends DatabaseException {
}