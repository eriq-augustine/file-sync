package dev.eriq.filesync.errors;

import dev.eriq.filesync.errors.FileSyncError;

/**
 * Errors that stem from the user providing bad input/arguments.
 */
public class BadInputError extends FileSyncError {
    public BadInputError(String message) {
        super(message);
    }

    public BadInputError(String message, Throwable cause) {
        super(message, cause);
    }
}
