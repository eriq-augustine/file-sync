package dev.eriq.filesync;

import dev.eriq.filesync.location.Location;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/**
 * A sync action between two locations.
 */
public class Sync {
    /**
     * Checks that are used to see if two dirents (with the same name and type) are the same.
     *
     * Additional checks may be costly or not useful (depending on the underlying location).
     * For example, an underlying file system may not keep an accurate mod time,
     * or a size check may require an additional network request.
     */
    public enum IdentityCheck {
        /**
         * Check the file size (in bytes).
        */
        SIZE,

        /**
         * Check the last modification time (in milliseconds).
        */
        MODTIME,
    }

    /**
     * How to handle conflicting files (or symlinks).
     *
     * Two dirents are only considered conflicting if they have the same name and type and if all specified IdentityChecks match.
     * If two dirents have the same name but not the same type,
     * then the destination dirent will just be considered out-of-date and it will be overwritten.
     *
     * These options do not apply to directories (as two directories are never considered in conflict).
    */
    public enum ConflictBehavior {
        /**
         * Skip syncing this file.
         */
        SKIP,

        /**
         * Overwrite the destination file.
         */
        OVERWRITE,
    }

    private Location source;
    private Location dest;
    private EnumSet<IdentityCheck> identityChecks;
    private ConflictBehavior conflictBehavior;

    /**
     * Construct a sync action.
     *
     * This object takes ownership of the locations.
     */
    public Sync(Location source, Location dest, EnumSet<IdentityCheck> identityChecks, ConflictBehavior conflictBehavior) {
        this.source = source;
        this.dest = dest;
        this.identityChecks = identityChecks;
        this.conflictBehavior = conflictBehavior;
    }

    /**
     * Convenience constructor that has no extra identity checks and skips conflicts.
     */
    public Sync(Location source, Location dest) {
        this(source, dest, EnumSet.noneOf(IdentityCheck.class), ConflictBehavior.SKIP);
    }

    /**
     * Run a sync between the two locations.
     *
     * Syncing will happen one directory at a time in a breadth-first fashion.
     */
    public void sync() throws Exception {
        try {
            source.open();
            dest.open();

            this.syncFull();
        } finally {
            source.close();
            dest.close();
        }
    }

    private void syncFull() throws IOException {
        // Create a fringe initially populated with the current directory.
        Queue<String> fringe = new LinkedList<String>();
        fringe.add(".");

        // Sync each directory until the fringe is empty.
        while (!fringe.isEmpty()) {
            this.syncDir(fringe.remove(), fringe);
        }
    }

    /**
     * Sync a single dir.
     * Any discovered directories will be placed into the fringe.
     */
    private void syncDir(String relpath, Queue<String> fringe) throws IOException {
        // {name: info, ...}.
        Map<String, Location.DirentInfo> destDirents = new HashMap<String, Location.DirentInfo>();
        for (Location.DirentInfo dirent : this.dest.list(relpath)) {
            destDirents.put(dirent.getName(), dirent);
        }

        for (Location.DirentInfo dirent : this.source.list(relpath)) {
            String direntRelpath = this.source.join(relpath, dirent.getName());

            // Check for and handle any conflicts.
            if (destDirents.containsKey(dirent.getName()) && this.isConflict(dirent, destDirents.get(dirent.getName()))) {
                if (this.conflictBehavior == ConflictBehavior.SKIP) {
                    continue;
                }
            }

            Path tempPath = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());

            // Download the dirent.
            this.source.fetch(direntRelpath, tempPath);

            // Upload the dirent.
            this.dest.put(tempPath, direntRelpath);

            // Remove the temp path.
            Util.removeDirent(tempPath);

            // If this is a dir, put it on the fringe for later syncing.
            if (dirent.isDir()) {
                fringe.add(direntRelpath);
            }
        }
    }

    private boolean isConflict(Location.DirentInfo a, Location.DirentInfo b) {
        if ((a.getType() != b.getType()) || !a.getName().equals(b.getName())) {
            return false;
        }

        if (this.identityChecks.contains(IdentityCheck.SIZE) && (a.getSize() != b.getSize())) {
            return false;
        }

        if (this.identityChecks.contains(IdentityCheck.MODTIME) && (a.getModTime() != b.getModTime())) {
            return false;
        }

        return true;
    }
}
