package dev.eriq.filesync.location;

import dev.eriq.filesync.location.LocalLocation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * An abstraction of both a location where files can be synced to/from,
 * and the mechanisms for working with data in that location.
 *
 * A location can be on this current machine (local) or on a different machine (remote).
 *
 * Locations support very simple operations (fetch/read and put/write) on their dirents.
 * All IO operations are NOT recursive,
 * callers are responsible for handling recursive dirent operations.
 */
public abstract class Location implements AutoCloseable {
    /**
     * The directory within the location to work in.
     */
    protected String baseDir;

    public Location(String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Create a location from a string using specific conventions for this project.
     */
    public static Location fromString(String text) {
        // If there are no additional markings, then it must be the path for a local location.
        return new LocalLocation(Path.of(text));
    }

    /**
     * Initialize a connection to the given location.
     * This must be called before calling any operations (list, fetch, put).
     * If the location is closed, then it needs to be opened again before calling operations.
     *
     * This method should throw if there are any issues connecting/locating the location or base dir.
     */
    public abstract void open() throws Exception;

    /**
     * Close this location and release all resources.
     * A closed location can be opened again.
     */
    public abstract void close() throws Exception;

    /**
     * Fetch a dirent (identified by the relative path),
     * and place it at the given temp path.
     * The temp path should not exist before the call.
     */
    public abstract void fetch(String relpath, Path tempPath) throws IOException;

    /**
     * Put a dirent (identified by the temp path),
     * and place it at the given relative path within this location.
     * Any existing dirent at the relative path should be deleted.
     * The temp path must exist before the call.
     */
    public abstract void put(Path tempPath, String relpath) throws IOException;

    /**
     * List a directory (identified by the relative path).
     */
    public abstract List<DirentInfo> list(String relpath) throws IOException;

    /**
     * Join two elements of a relative path together for this location.
     * Locations must guarantee that using this with the argument to list() and the resulting dirent names should resolve to valid relative paths.
     */
    public abstract String join(String a, String b);

    /**
     * A simple base representation of information about a dirent.
     */
    public abstract static class DirentInfo {
        public enum Type {
            DIR,
            FILE,
            SYMLINK,
            OTHER,
        }

        protected String name;
        protected Type type;

        public DirentInfo(String name, Type type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return this.name;
        }

        public Type getType() {
            return this.type;
        }

        public boolean equals(Object object) {
            if (object == this) {
                return true;
            }

            if ((object == null) || !(object instanceof DirentInfo)) {
                return false;
            }

            DirentInfo other = (DirentInfo)object;
            return (this.type == other.type) && (this.name.equals(other.name));
        }

        public boolean isDir() {
            return (this.type == Type.DIR);
        }

        /**
         * Get the dirent size in bytes.
         * This number should only be seriously used for files.
         * Other types may return reasonable values, but they should not be relied on.
         */
        public abstract long getSize();

        /**
         * Get the modification time as the number of milliseconds since the UNIX epoch.
         */
        public abstract long getModTime();
    }
}
