package dev.eriq.filesync;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class Util {
    public static final int PORT_MIN = 0;

    public static final int PORT_MAX = (1 << 16) -1;

    public static final int DEFAULT_PORT_SEARCH_START = 30000;
    public static final int DEFAULT_PORT_SEARCH_END = 40000;

    private static int portSearchCount = 0;

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

    /**
     * Check if the specified TCP port is available for use on this system.
     */
    public static boolean portAvailable(int port) {
        if ((port < PORT_MIN) || (port > PORT_MAX)) {
            throw new IllegalArgumentException(String.format("Port (%d) is out of range [%d, %d].", port, PORT_MIN, PORT_MAX));
        }

        ServerSocket server = null;
        try {
            server = new ServerSocket(port);
            server.setReuseAddress(true);

            // Port is open!
            return true;
        } catch (IOException ex) {
            // Port is closed.
            return false;
        } finally {
            if (server != null) {
                try {
                    server.close();
                } catch (Exception ex) {
                }
            }
        }
    }

    /**
     * Find an open port within the inclusive range,
     * throw if no port was found.
     */
    public static int findAvailablePort(int start, int end) {
        // Keep track of the number of times we have called this function to try and prevent timing issues with getting a port and using it.
        // This will cause subsequent calls to start at one more than the previous call (with wrapping).
        portSearchCount++;

        int range = (end - start + 1);
        for (int offset = 0; offset < range; offset++) {
            offset = (offset + portSearchCount) % range;

            int port = start + offset;
            if (portAvailable(port)) {
                return port;
            }
        }

        throw new RuntimeException("Could not find open port.");
    }

    public static int findAvailablePort() {
        return findAvailablePort(DEFAULT_PORT_SEARCH_START, DEFAULT_PORT_SEARCH_END);
    }
}
