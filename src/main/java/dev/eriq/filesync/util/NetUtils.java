package dev.eriq.filesync.util;

import java.io.IOException;
import java.net.ServerSocket;

public class NetUtils {
    public static final int PORT_MIN = 0;

    public static final int PORT_MAX = (1 << 16) -1;

    public static final int DEFAULT_PORT_SEARCH_START = 30000;
    public static final int DEFAULT_PORT_SEARCH_END = 40000;

    private static int portSearchCount = 0;

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
