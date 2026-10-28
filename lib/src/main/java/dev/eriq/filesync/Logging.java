package dev.eriq.filesync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Logging {
    public static final String FORMAT = "[%1$tF %1$tT] [%4$-7s] %3$s :: %5$s %n";

    public static final Level PROJECT_LEVEL = Level.INFO;

    private static final Map<String, Level> DEFAULT_LOGGING_LEVELS = Map.ofEntries(
        Map.entry(Logger.GLOBAL_LOGGER_NAME, Level.WARNING),
        Map.entry("", Level.WARNING),

        Map.entry("net.schmizz.sshj", Level.WARNING),

        Map.entry("dev.eriq.filesync", PROJECT_LEVEL)
    );

    private static List<Logger> activeLoggers = new ArrayList<Logger>();

    static {
        init();
    }

    /**
     * Get a logger that is registered with this project.
     */
    public static Logger getLogger(String name) {
        Logger logger = Logger.getLogger(name);

        activeLoggers.add(logger);
        logger.setLevel(PROJECT_LEVEL);

        return logger;
    }

    /**
     * Initialize the logging infrastructure for this project.
     */
    public static void init() {
        System.setProperty("java.util.logging.SimpleFormatter.format", FORMAT);

        setLevelAll();

        // Set all loggers that have already been initialized.
        for (Logger logger : activeLoggers) {
            logger.setLevel(PROJECT_LEVEL);
        }
    }

    /**
     * Set the level for a specific logger and all attached handlers.
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
            setLevel(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Set a specific level for all the logger we know about in DEFAULT_LOGGING_LEVELS.
     */
    public static void setLevelAll(Level level) {
        for (String name : DEFAULT_LOGGING_LEVELS.keySet()) {
            setLevel(name, level);
        }
    }
}
