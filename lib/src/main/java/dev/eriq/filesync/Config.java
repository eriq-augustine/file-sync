package dev.eriq.filesync;

import dev.eriq.filesync.errors.IOError;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    public static final String PROJECT_PROPS = "project.properties";

    private static Properties properties = new Properties();

    static {
        init();
    }

    private Config() {}

    public static String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    private static void init() {
        InputStream stream = ClassLoader.getSystemClassLoader().getResourceAsStream(PROJECT_PROPS);
        if (stream == null) {
            return;
        }

        try {
            properties.load(stream);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to load config from '%s'.", PROJECT_PROPS), ex);
        } finally {
            try {
                stream.close();
            } catch (IOException ex) {
                // Ignore
            }
        }
    }
}
