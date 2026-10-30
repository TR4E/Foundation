package me.trae.foundation.database.api.utility;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.exception.DatabaseException;

import java.util.function.Consumer;

/**
 * User code always runs on its own virtual thread, never on a database or Redis worker.
 */
@UtilityClass
public final class DatabaseCallbacks {

    private static final System.Logger LOGGER = System.getLogger(DatabaseCallbacks.class.getName());

    public static void dispatch(final Runnable runnable, final Consumer<DatabaseException> failureConsumer) {
        Thread.ofVirtual().name("database-callback").start(() -> {
            try {
                runnable.run();
            } catch (final RuntimeException | Error exception) {
                report(failureConsumer, new DatabaseException("Database callback failed", exception));
            }
        });
    }

    public static void report(final Consumer<DatabaseException> failureConsumer, final DatabaseException exception) {
        Thread.ofVirtual().name("database-failure-handler").start(() -> {
            try {
                failureConsumer.accept(exception);
            } catch (final RuntimeException | Error handlerException) {
                LOGGER.log(System.Logger.Level.ERROR, "Application database failure handler threw", handlerException);
                LOGGER.log(System.Logger.Level.ERROR, "Original database failure", exception);
            }
        });
    }
}