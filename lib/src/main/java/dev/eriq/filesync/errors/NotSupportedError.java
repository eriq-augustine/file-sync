package dev.eriq.filesync.errors;

import dev.eriq.filesync.errors.FileSyncError;

/**
 * Errors that stem from unsupported operations.
 */
public class NotSupportedError extends FileSyncError {
    public NotSupportedError(String message) {
        super(message);
    }

    public NotSupportedError(String message, Throwable cause) {
        super(message, cause);
    }
}
