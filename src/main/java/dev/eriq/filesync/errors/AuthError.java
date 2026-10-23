package dev.eriq.filesync.errors;

import dev.eriq.filesync.errors.FileSyncError;

/**
 * Errors that stem from authentication failure.
 */
public class AuthError extends FileSyncError {
    public AuthError(String message) {
        super(message);
    }

    public AuthError(String message, Throwable cause) {
        super(message, cause);
    }
}
