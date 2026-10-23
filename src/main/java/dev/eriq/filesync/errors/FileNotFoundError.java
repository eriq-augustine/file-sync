package dev.eriq.filesync.errors;

import dev.eriq.filesync.errors.IOError;

/**
 * Errors that stem from missing files.
 */
public class FileNotFoundError extends IOError {
    public FileNotFoundError(String message) {
        super(message);
    }

    public FileNotFoundError(String message, Throwable cause) {
        super(message, cause);
    }
}
