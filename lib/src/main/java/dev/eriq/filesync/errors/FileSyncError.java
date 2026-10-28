package dev.eriq.filesync.errors;

/**
 * A base class for errors thrown from within this project.
 *
 * Note that these errors are unchecked for cleaner code,
 * but require more responsibility from the developer.
 */
public class FileSyncError extends RuntimeException {
    public FileSyncError(String message) {
        super(message);
    }

    public FileSyncError(String message, Throwable cause) {
        super(message, cause);
    }
}
