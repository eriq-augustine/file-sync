package dev.eriq.filesync;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Logging {
    public static final String FORMAT = "[%1$tF %1$tT] [%4$-7s] %3$s :: %5$s %n";

    private static final Map<String, Level> DEFAULT_LOGGING_LEVELS = Map.ofEntries(
        Map.entry(Logger.GLOBAL_LOGGER_NAME, Level.WARNING),
        Map.entry("dev.eriq.filesync", Level.INFO),
        Map.entry("net.schmizz.sshj", Level.WARNING)
    );

    static {
        init();
    }

    /**
     * Initialize the logging infrastructure for this project.
     */
    public static void init() {
        setLevelAll();
        System.setProperty("java.util.logging.SimpleFormatter.format", FORMAT);
    }

    /**
     * Set the level for a specific logger.
     */
    public static void setLevel(String name, Level level) {
        Logger.getLogger(name).setLevel(level);
    }

    /**
     * Set the level for this project's logger.
     */
    public static void setLevel(Level level) {
        setLevel("dev.eriq.filesync", level);
    }

    /**
     * Set the respective default level for all the logger we know about in DEFAULT_LOGGING_LEVELS.
     */
    public static void setLevelAll() {
        for (Map.Entry<String, Level> entry : DEFAULT_LOGGING_LEVELS.entrySet()) {
            Logger.getLogger(entry.getKey()).setLevel(entry.getValue());
        }
    }

    /**
     * Set a specific level for all the logger we know about in DEFAULT_LOGGING_LEVELS.
     */
    public static void setLevelAll(Level level) {
        for (String name : DEFAULT_LOGGING_LEVELS.keySet()) {
            Logger.getLogger(name).setLevel(level);
        }
    }
}
