package dev.eriq.filesync;

import dev.eriq.filesync.Logging;
import dev.eriq.filesync.errors.FileSyncError;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.util.DirentUtils;
import dev.eriq.filesync.util.JSON;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * A sync action between two locations.
 */
public class Sync extends JSON.MapConverter {
    private static final Logger logger = Logging.getLogger(Sync.class.getName());

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

    protected void serializeToMap(Map<String, Object> data) {
        data.put("source", source.toMap());
        data.put("dest", dest.toMap());

        List<String> stringIdentityChecks = new ArrayList<String>();
        for (IdentityCheck identityCheck : identityChecks) {
            stringIdentityChecks.add(identityCheck.toString());
        }

        Collections.sort(stringIdentityChecks);
        data.put("identity-checks", stringIdentityChecks);

        data.put("conflict-behavior", conflictBehavior.toString());
    }

    public static Object fromMap(Map<String, Object> data) {
        Location source = (Location)(JSON.fromMap((Map)(data.get("source"))));
        Location dest = (Location)(JSON.fromMap((Map)(data.get("dest"))));

        EnumSet<IdentityCheck> identityChecks = EnumSet.noneOf(IdentityCheck.class);
        for (String rawCheck : (List<String>)data.get("identity-checks")) {
            identityChecks.add(IdentityCheck.valueOf(rawCheck));
        }

        ConflictBehavior conflictBehavior = ConflictBehavior.valueOf(data.get("conflict-behavior").toString());

        return new Sync(source, dest, identityChecks, conflictBehavior);
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (other == null || !(other instanceof Sync)) {
            return false;
        }

        Sync otherSync = (Sync)other;
        return (
            this.source.equals(otherSync.source)
            && this.dest.equals(otherSync.dest)
            && this.identityChecks.equals(otherSync.identityChecks)
            && this.conflictBehavior.equals(otherSync.conflictBehavior)
        );
    }

    /**
     * Run a sync between the two locations.
     *
     * Syncing will happen one directory at a time in a breadth-first fashion.
     */
    public void sync() {
        try {
            source.open();
            dest.open();

            syncFull();
        } finally {
            source.close();
            dest.close();
        }
    }

    private void syncFull() {
        // Create a fringe initially populated with the current directory.
        Queue<String> fringe = new LinkedList<String>();
        fringe.add(".");

        // Sync each directory until the fringe is empty.
        while (!fringe.isEmpty()) {
            String nextDir = fringe.remove();
            try {
                syncDir(nextDir, fringe);
            } catch (FileSyncError ex) {
                logger.log(java.util.logging.Level.WARNING, String.format("Failed to sync directory: '%s'.", nextDir), ex);
            }
        }
    }

    /**
     * Sync a single dir.
     * Any discovered directories will be placed into the fringe.
     */
    private void syncDir(String relpath, Queue<String> fringe) {
        // Build a map of dirents currently in the dest dir.
        // {name: info, ...}.
        Map<String, Location.DirentInfo> destDirents = new HashMap<String, Location.DirentInfo>();
        for (Location.DirentInfo dirent : dest.listDir(relpath)) {
            destDirents.put(dirent.getName(), dirent);
        }

        for (Location.DirentInfo dirent : source.listDir(relpath)) {
            String direntRelpath = source.joinPath(relpath, dirent.getName());

            try {
                syncDir(dirent, direntRelpath, destDirents, fringe);
            } catch (FileSyncError ex) {
                logger.log(java.util.logging.Level.WARNING, String.format("Failed to sync dirent: '%s'.", direntRelpath), ex);
            }
        }
    }

    /**
     * Sync a single dirent.
     * Any discovered directories will be placed into the fringe.
     */
    private void syncDir(Location.DirentInfo dirent, String relpath, Map<String, Location.DirentInfo> destDirents, Queue<String> fringe) {
        // Check for and handle any conflicts.
        if (destDirents.containsKey(dirent.getName())) {
            // Check for a true conflict.
            if (isConflict(dirent, destDirents.get(dirent.getName()))) {
                // This is a conflict, decide what to do.
                if (conflictBehavior == ConflictBehavior.SKIP) {
                    return;
                }
            }

            // There is a dirent with the same name, and we are not skipping.
            // It must be removed in the dest.
            dest.removeDirent(relpath, destDirents.get(dirent.getName()));
        }

        switch (dirent.getType()) {
            case DIR:
                dest.makeDir(relpath);

                // Add dirs to the fringe.
                fringe.add(relpath);

                break;
            case FILE:
                Path tempPath = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());

                try {
                    // Download the dirent.
                    source.fetchFile(relpath, tempPath);

                    // Upload the dirent.
                    dest.putFile(tempPath, relpath);
                } finally {
                    // Remove the temp path.
                    DirentUtils.removeDirent(tempPath);
                }

                break;
            case SYMLINK:
                dest.createLink(relpath, source.readLink(relpath));
                break;
        }
    }

    private boolean isConflict(Location.DirentInfo a, Location.DirentInfo b) {
        if ((a.getType() != b.getType()) || !a.getName().equals(b.getName())) {
            return false;
        }

        if (identityChecks.contains(IdentityCheck.SIZE) && (a.getSize() != b.getSize())) {
            return false;
        }

        if (identityChecks.contains(IdentityCheck.MODTIME) && (a.getModTime() != b.getModTime())) {
            return false;
        }

        return true;
    }
}
