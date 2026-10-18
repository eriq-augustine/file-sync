package dev.eriq.filesync;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class Util {
    /**
     * Check for a dirent's existence using this project's standard rules.
     * This will not follow links.
     */
    public static boolean exists(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Check if a dirent exists and is a dir using this project's standard rules.
     * This will not follow links.
     */
    public static boolean isDir(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Check if a dirent exists and is a regular file using this project's standard rules.
     * This will not follow links.
     */
    public static boolean isFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Check if a dirent exists and is a symbolic link using this project's standard rules.
     * This will not follow links.
     */
    public static boolean isLink(Path path) {
        return Files.isSymbolicLink(path);
    }

    /**
     * Get a dirent's mod time (in MS) using this project's standard rules.
     * This will not follow links.
     */
    public static long getModTime(Path path) throws IOException {
        return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
    }

    /**
     * Remove a dirent, which can be a populated directory.
     */
    public static void removeDirent(Path path) throws IOException {
        if (!exists(path)) {
            return;
        }

        // Handle symlinks first.
        if (isLink(path)) {
            Files.delete(path);
            return;
        }

        // Other (non-dir) dirent types should be simple.
        if (!isDir(path)) {
            Files.delete(path);
            return;
        }

        // For dirs, first delete all children, then remove the dir.
        for (File dirent : path.toFile().listFiles()) {
            removeDirent(dirent.toPath());
        }
        Files.delete(path);
    }
}
