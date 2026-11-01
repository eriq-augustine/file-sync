package dev.eriq.filesync.util;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Errors {
    private Errors() {}

    /**
     * Get a string representation that is good for logging.
     */
    public static String toLogString(Throwable throwable) {
        return String.format("%s\n---\n%s\n---", throwable.getMessage(), stackTraceToString(throwable));
    }

    public static String stackTraceToString(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        return stringWriter.toString();
    }
}
