package dev.eriq.filesync.errors;

import java.io.PrintWriter;
import java.io.StringWriter;

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

    /**
     * Get a string representation that is good for logging.
     */
    public String toLogString() {
        return String.format("%s\n---\n%s\n---", getMessage(), stackTraceToString(this));
    }

    public static String stackTraceToString(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        return stringWriter.toString();
    }
}
