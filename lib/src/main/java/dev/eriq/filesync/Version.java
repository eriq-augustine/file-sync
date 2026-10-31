package dev.eriq.filesync;

import dev.eriq.filesync.Config;

public class Version {
    public static final String UNKNOWN_VERSION = "UNKNOWN";

    public static final String VERSION_KEY = "project.version";

    private Version() {}

    public static String get() {
        return Config.get(VERSION_KEY, UNKNOWN_VERSION);
    }
}
