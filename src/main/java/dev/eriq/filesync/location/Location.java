package dev.eriq.filesync.location;

import dev.eriq.filesync.errors.BadInputError;
import dev.eriq.filesync.errors.IOError;
import dev.eriq.filesync.util.JSON;

import dev.eriq.filesync.location.LocalLocation;
import dev.eriq.filesync.location.SSHLocation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * An abstraction of both a location where files can be synced to/from,
 * and the mechanisms for working with data in that location.
 *
 * A location can be on this current machine (local) or on a different machine (remote).
 *
 * Locations support very simple operations on their dirents.
 * Operations identify files within the location using a relative path (relative to the location's base dir),
 * and identify files on this local machine (usually referred to as "temp files") with a java.nio.file.Path.
 *
 * All IO operations are NOT recursive,
 * callers are responsible for handling recursive dirent operations.
 *
 * Operations are meant to be very targeted and generally do one thing.
 * This means that, for example, write operations expect the target path for a write to not exist before a write begins.
 */
public abstract class Location extends JSON.MapConverter implements AutoCloseable {
    /**
     * The directory within the location to work in.
     */
    protected String baseDir;

    public Location(String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Create a location from a string using specific conventions for this project.
     *
     * SSH Location: `ssh::<user>:<password>@<host>[:port][::<path>]`
     * Local Location: `local::<path>`
     * Additionally, if a string has no initial marking, then it is considered a local location.
     */
    public static Location fromString(String text) {
        String[] parts = text.trim().split("::", 2);

        // No initial marking was provided.
        if (parts.length == 1) {
            return new LocalLocation(Path.of(text));
        }

        String type = parts[0].toLowerCase();
        switch (type) {
            case "local":
                return new LocalLocation(Path.of(parts[1]));
            case "ssh":
                return SSHLocation.fromString(parts[1]);
            default:
                throw new BadInputError(String.format("Unknown location type ('%s') for: '%s'.", type, text));
        }
    }

    protected void serializeToMap(Map<String, Object> data) {
        data.put("base-dir", baseDir);
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (other == null || !(other instanceof Location)) {
            return false;
        }

        Location otherLocation = (Location)other;
        return this.baseDir.equals(otherLocation.baseDir);
    }

    /**
     * Initialize a connection to the given location.
     * This must be called before calling any operations (list, fetch, put).
     * If the location is closed, then it needs to be opened again before calling operations.
     *
     * This method should throw if there are any issues connecting/locating the location or base dir.
     */
    public abstract void open();

    /**
     * Close this location and release all resources.
     * A closed location can be opened again.
     */
    public abstract void close();

    /**
     * Join two elements of a relative path together for this location.
     * Locations must guarantee that using this with the argument to list() and the resulting dirent names should resolve to valid relative paths.
     */
    public abstract String joinPath(String a, String b);

    /**
     * List a directory.
     */
    public abstract List<DirentInfo> listDir(String relpath);

    /**
     * Make a directory.
     * Parent non-existent directories are not created.
     */
    public abstract void makeDir(String relpath);

    /**
     * Remove an empty directory.
     */
    public abstract void removeDir(String relpath);

    /**
     * Fetch a file from this location and place it at the given temp path.
     */
    public abstract void fetchFile(String relpath, Path tempPath);

    /**
     * Put a file into this location.
     */
    public abstract void putFile(Path tempPath, String relpath);

    /**
     * Remove a file.
     */
    public abstract void removeFile(String relpath);

    /**
     * Read the target of a link.
     */
    public abstract String readLink(String relpath);

    /**
     * Create a link with the given target.
     */
    public abstract void createLink(String relpath, String target);

    /**
     * Remove a link.
     */
    public abstract void removeLink(String relpath);

    /**
     * Convenience method that calls the correct method for the dirent type.
     */
    public void removeDirent(String relpath, DirentInfo direntInfo) {
        switch (direntInfo.getType()) {
            case DIR:
                removeDirRecursive(relpath);
                break;
            case FILE:
                removeFile(relpath);
                break;
            case SYMLINK:
                removeLink(relpath);
                break;
            default:
                throw new IOError(String.format("Unknown file mode ('%s') when removing '%s'.", direntInfo.getType(), relpath));
        }
    }

    /**
     * A convenience function for remove a directory and all its contents.
     */
    public void removeDirRecursive(String relpath) {
        for (DirentInfo dirent : listDir(relpath)) {
            removeDirent(joinPath(relpath, dirent.getName()), dirent);
        }

        removeDir(relpath);
    }

    /**
     * A simple base representation of information about a dirent.
     * Location implementations may override this to provide lazy access to attributes that may have a non-trivial cost to find.
     */
    public static class DirentInfo {
        public enum Type {
            DIR,
            FILE,
            SYMLINK,
            OTHER,
        }

        protected String name;
        protected Type type;
        protected long size;
        protected long modTimeMS;

        public DirentInfo(String name, Type type, long size, long modTimeMS) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.modTimeMS = modTimeMS;
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public long getSize() {
            return size;
        }

        public long getModTime() {
            return modTimeMS;
        }

        public boolean equals(Object object) {
            if (object == this) {
                return true;
            }

            if ((object == null) || !(object instanceof DirentInfo)) {
                return false;
            }

            DirentInfo other = (DirentInfo)object;
            return ((type == other.type) && (name.equals(other.name)) && (size == other.size) && (modTimeMS == other.modTimeMS));
        }

        public boolean isDir() {
            return (type == Type.DIR);
        }
    }
}
