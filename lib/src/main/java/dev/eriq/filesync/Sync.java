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

    public Location getSource() {
        return source;
    }

    public Location getDest() {
        return dest;
    }

    public EnumSet<IdentityCheck> getIdentityChecks() {
        return identityChecks;
    }

    public ConflictBehavior getConflictBehavior() {
        return conflictBehavior;
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
     * Test that this sync is valid.
     * Return null on success, and an error message on failure.
     */
    public String test() {
        String sourceResult = source.test();
        String destResult = dest.test();

        List<String> results = new ArrayList<String>(2);

        if (sourceResult != null) {
            results.add(String.format("Source error: '%s'.", sourceResult));
        }

        if (destResult != null) {
            results.add(String.format("Dest error: '%s'.", destResult));
        }

        if (results.size() == 0) {
            return null;
        }

        return String.join(" ", results);
    }

    /**
     * Run a sync between the two locations.
     *
     * Syncing will happen one directory at a time in a breadth-first fashion.
     */
    public SyncResult sync() {
        SyncResult result = new SyncResult();

        try {
            source.open();
            dest.open();

            syncFull(result);
        } finally {
            source.close();
            dest.close();
        }

        return result;
    }

    private void syncFull(SyncResult result) {
        // Create a fringe initially populated with the current directory.
        Queue<String> fringe = new LinkedList<String>();
        fringe.add(".");

        // Sync each directory until the fringe is empty.
        while (!fringe.isEmpty()) {
            String nextDir = fringe.remove();
            try {
                syncDir(result, nextDir, fringe);
            } catch (FileSyncError ex) {
                logger.warning(String.format("Failed to sync directory: '%s'.\n%s", nextDir, ex.toLogString()));
            }
        }
    }

    /**
     * Sync a single dir.
     * Any discovered directories will be placed into the fringe.
     */
    private void syncDir(SyncResult result, String relpath, Queue<String> fringe) {
        // Build a map of dirents currently in the dest dir.
        // {name: info, ...}.
        Map<String, Location.DirentInfo> destDirents = new HashMap<String, Location.DirentInfo>();
        for (Location.DirentInfo dirent : dest.listDir(relpath)) {
            destDirents.put(dirent.getName(), dirent);
        }

        result.directoriesExpanded++;
        for (Location.DirentInfo dirent : source.listDir(relpath)) {
            String direntRelpath = source.joinPath(relpath, dirent.getName());

            try {
                syncDir(result, dirent, direntRelpath, destDirents, fringe);
            } catch (FileSyncError ex) {
                result.errorCount++;
                logger.warning(String.format("Failed to sync dirent: '%s'.\n%s", direntRelpath, ex.toLogString()));
            }
        }
    }

    /**
     * Sync a single dirent.
     * Any discovered directories will be placed into the fringe.
     */
    private void syncDir(SyncResult result, Location.DirentInfo dirent, String relpath, Map<String, Location.DirentInfo> destDirents, Queue<String> fringe) {
        // Check for a name match.
        // A match may mean a conflict, but not necessarily (e.g., it just a matching directory).
        Location.DirentInfo destDirent = destDirents.get(dirent.getName());
        if (destDirent != null) {
            // If both of these existing dirents are dirs, then there is no conflict just add the dir to the fringe and carry on.
            if (dirent.isDir() && destDirent.isDir()) {
                fringe.add(relpath);
                return;
            }

            // Check for a true conflict.
            if (isConflict(dirent, destDirent)) {
                // This is a conflict, decide what to do.
                if (conflictBehavior == ConflictBehavior.SKIP) {
                    result.direntsSkipped++;
                    return;
                }
            }

            // There is a dirent with the same name, and we are not skipping.
            // It must be removed in the dest.
            dest.removeDirent(relpath, destDirent);
            result.direntsOverwritten++;
        }

        switch (dirent.getType()) {
            case DIR:
                dest.makeDir(relpath);
                result.directoriesCreated++;

                // Add dirs to the fringe.
                fringe.add(relpath);

                break;
            case FILE:
                Path tempPath = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
                result.filesCreated++;

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
                result.linksCreated++;
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

    public static class SyncResult extends JSON.MapConverter {
        public int directoriesExpanded;

        public int directoriesCreated;
        public int filesCreated;
        public int linksCreated;

        public int direntsSkipped;
        public int direntsOverwritten;

        public int errorCount;

        public SyncResult(int directoriesExpanded, int directoriesCreated, int filesCreated, int linksCreated, int direntsSkipped, int direntsOverwritten, int errorCount) {
            this.directoriesExpanded = directoriesExpanded;
            this.directoriesCreated = directoriesCreated;
            this.filesCreated = filesCreated;
            this.linksCreated = linksCreated;
            this.direntsSkipped = direntsSkipped;
            this.direntsOverwritten = direntsOverwritten;
            this.errorCount = errorCount;
        }

        public SyncResult() {
            this(0, 0, 0, 0, 0, 0, 0);
        }

        public int direntsCreated() {
            return directoriesCreated + filesCreated + linksCreated;
        }

        protected void serializeToMap(Map<String, Object> data) {
            data.put("directoriesExpanded", directoriesExpanded);
            data.put("directoriesCreated", directoriesCreated);
            data.put("filesCreated", filesCreated);
            data.put("linksCreated", linksCreated);
            data.put("direntsSkipped", direntsSkipped);
            data.put("direntsOverwritten", direntsOverwritten);
            data.put("errorCount", errorCount);
        }

        public static Object fromMap(Map<String, Object> data) {
            SyncResult result = new SyncResult();

            result.directoriesExpanded = ((Integer)data.get("directoriesExpanded")).intValue();
            result.directoriesCreated = ((Integer)data.get("directoriesCreated")).intValue();
            result.filesCreated = ((Integer)data.get("filesCreated")).intValue();
            result.linksCreated = ((Integer)data.get("linksCreated")).intValue();
            result.direntsSkipped = ((Integer)data.get("direntsSkipped")).intValue();
            result.direntsOverwritten = ((Integer)data.get("direntsOverwritten")).intValue();
            result.errorCount = ((Integer)data.get("errorCount")).intValue();

            return result;
        }

        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }

            if (other == null || !(other instanceof SyncResult)) {
                return false;
            }

            SyncResult otherResult = (SyncResult)other;
            return (
                this.directoriesExpanded == otherResult.directoriesExpanded
                && this.directoriesCreated == otherResult.directoriesCreated
                && this.filesCreated == otherResult.filesCreated
                && this.linksCreated == otherResult.linksCreated
                && this.direntsSkipped == otherResult.direntsSkipped
                && this.direntsOverwritten == otherResult.direntsOverwritten
                && this.errorCount == otherResult.errorCount
            );
        }
    }
}
