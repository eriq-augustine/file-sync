package dev.eriq.filesync.errors;

import dev.eriq.filesync.errors.FileSyncError;

/**
 * Errors that stem from IO issues.
 */
public class IOError extends FileSyncError {
    public IOError(String message) {
        super(message);
    }

    public IOError(String message, Throwable cause) {
        super(message, cause);
    }
}
