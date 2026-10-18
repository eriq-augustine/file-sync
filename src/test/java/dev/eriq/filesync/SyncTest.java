package dev.eriq.filesync;

import dev.eriq.filesync.Util;
import dev.eriq.filesync.location.LocalLocation;
import dev.eriq.filesync.location.Location;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;
import java.util.List;

public class SyncTest {
    @Test
    public void testSyncToLocal(
            @TempDir Path sourceTempDir, @TempDir Path destTempDir,
            @TempDir Path extraTempDir1, @TempDir Path extraTempDir2) throws Exception {
        List<SyncTestCase> testCases = List.of(
            // Empty
            new SyncTestCase(
                new LocalLocation(extraTempDir1),
                new LocalLocation(extraTempDir2),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.SKIP,
                List.of()
            ),

            // Text File
            new SyncTestCase(
                new LocalLocation(extraTempDir1),
                new LocalLocation(extraTempDir2),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestFile("a.txt", "abc")
                )
            ),

            // Dir
            new SyncTestCase(
                new LocalLocation(extraTempDir1),
                new LocalLocation(extraTempDir2),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestDir("somedir")
                )
            ),

            // Link (Broken)
            new SyncTestCase(
                new LocalLocation(extraTempDir1),
                new LocalLocation(extraTempDir2),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestLink("someLink.txt", "someFile.txt")
                )
            ),

            // Link
            new SyncTestCase(
                new LocalLocation(extraTempDir1),
                new LocalLocation(extraTempDir2),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestFile("someFile.txt", "abc"),
                    new TestLink("someLink.txt", "someFile.txt")
                )
            ),

            // Multiple Dirents
            new SyncTestCase(
                new LocalLocation(extraTempDir1),
                new LocalLocation(extraTempDir2),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestFile("a.txt", "abc"),
                    new TestFile("dir1/b.txt", "123"),
                    new TestFile("dir1/dir2/c.txt", "abc123")
                )
            )
        );

        for (int i = 0; i < testCases.size(); i++) {
            SyncTestCase testCase = testCases.get(i);

            try {
                // Clear extra temp dirs.
                Util.removeDirent(extraTempDir1);
                Files.createDirectories(extraTempDir1);

                Util.removeDirent(extraTempDir2);
                Files.createDirectories(extraTempDir2);

                // Setup the initial source files.
                loadTestDir(sourceTempDir, testCase.dirents);

                // Setup the final destination.
                Util.removeDirent(destTempDir);
                Files.createDirectories(destTempDir);

                // Sync files from the initial source to the test source.
                Sync sync = new Sync(new LocalLocation(sourceTempDir), testCase.source);
                sync.sync();

                // Sync between test case locations.
                sync = new Sync(testCase.source, testCase.dest, testCase.identityChecks, testCase.conflictBehavior);
                sync.sync();

                // Sync from the test case destination and the empty test dir.
                sync = new Sync(testCase.dest, new LocalLocation(destTempDir));
                sync.sync();

                // Ensure the final output matches the specification.
                matchTestDir(destTempDir, testCase.dirents);
            } catch (AssertionError ex) {
                throw new AssertionError(String.format("Failed test case %d.", i), ex);
            } catch (Exception ex) {
                throw new AssertionError(String.format("Crashed test case %d.", i), ex);
            }
        }
    }

    @Test
    public void testSyncConflicts(
            @TempDir Path sourceTempDir, @TempDir Path destTempDir) throws Exception {
        List<SyncConflictTestCase> testCases = List.of(
            // Dirents with matching names (relpaths), but differing other attributes that result in no conflict.
            // This means that the target dirent will be overwritten.

            // !Conflict(!type, name)
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestFile("a.txt", "abc")
                ),
                List.of(
                    new TestDir("a.txt")
                ),
                List.of(
                    new TestFile("a.txt", "abc")
                )
            ),
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestDir("a.txt")
                ),
                List.of(
                    new TestFile("a.txt", "abc")
                ),
                List.of(
                    new TestDir("a.txt")
                )
            ),

            // !Conflict(type, name, !size)
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestFile("a.txt", "abc")
                ),
                List.of(
                    new TestFile("a.txt", "abc".repeat(100))
                ),
                List.of(
                    new TestFile("a.txt", "abc")
                )
            ),

            // !Conflict(type, name, !modtime)
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(0)
                ),
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(123)
                ),
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(0)
                )
            ),

            // !Conflict(type, name, size, !modtime)
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE, Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(0)
                ),
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(123)
                ),
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(0)
                )
            ),

            // !Conflict(type, name, !size, modtime)
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE, Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(0)
                ),
                List.of(
                    (new TestFile("a.txt", "abc".repeat(100))).setModTime(0)
                ),
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(0)
                )
            ),

            // !Conflict(type, name, !size, !modtime)
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE, Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(0)
                ),
                List.of(
                    (new TestFile("a.txt", "abc".repeat(100))).setModTime(123)
                ),
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(0)
                )
            ),

            // Conflict(type, name), Skip
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestFile("a.txt", "abc")
                ),
                List.of(
                    new TestFile("a.txt", "ABC")
                ),
                List.of(
                    new TestFile("a.txt", "ABC")
                )
            ),

            // Conflict(type, name), Overwrite
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.OVERWRITE,
                List.of(
                    new TestFile("a.txt", "abc")
                ),
                List.of(
                    new TestFile("a.txt", "ABC")
                ),
                List.of(
                    new TestFile("a.txt", "abc")
                )
            ),

            // Conflict(type, name, size), Skip
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestFile("a.txt", "abc")
                ),
                List.of(
                    new TestFile("a.txt", "ABC")
                ),
                List.of(
                    new TestFile("a.txt", "ABC")
                )
            ),

            // Conflict(type, name, size), Overwrite
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE),
                Sync.ConflictBehavior.OVERWRITE,
                List.of(
                    new TestFile("a.txt", "abc")
                ),
                List.of(
                    new TestFile("a.txt", "ABC")
                ),
                List.of(
                    new TestFile("a.txt", "abc")
                )
            ),

            // Conflict(type, name, modtime), Skip
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(123)
                ),
                List.of(
                    (new TestFile("a.txt", "ABC")).setModTime(123)
                ),
                List.of(
                    (new TestFile("a.txt", "ABC")).setModTime(123)
                )
            ),

            // Conflict(type, name, modtime), Overwrite
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.OVERWRITE,
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(123)
                ),
                List.of(
                    (new TestFile("a.txt", "ABC")).setModTime(123)
                ),
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(123)
                )
            ),

            // Conflict(type, name, size, modtime), Skip
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE, Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(123)
                ),
                List.of(
                    (new TestFile("a.txt", "ABC")).setModTime(123)
                ),
                List.of(
                    (new TestFile("a.txt", "ABC")).setModTime(123)
                )
            ),

            // Conflict(type, name, size, modtime), Overwrite
            new SyncConflictTestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE, Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.OVERWRITE,
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(123)
                ),
                List.of(
                    (new TestFile("a.txt", "ABC")).setModTime(123)
                ),
                List.of(
                    (new TestFile("a.txt", "abc")).setModTime(123)
                )
            )
        );

        for (int i = 0; i < testCases.size(); i++) {
            SyncConflictTestCase testCase = testCases.get(i);

            try {
                // Setup the source and dest dirs.
                loadTestDir(sourceTempDir, testCase.sourceDirents);
                loadTestDir(destTempDir, testCase.destDirents);

                // Sync between test case locations.
                Sync sync = new Sync(testCase.source, testCase.dest, testCase.identityChecks, testCase.conflictBehavior);
                sync.sync();

                // Ensure the final output matches the specification.
                matchTestDir(destTempDir, testCase.expectedDirents);
            } catch (AssertionError ex) {
                throw new AssertionError(String.format("Failed test case %d.", i), ex);
            } catch (Exception ex) {
                throw new AssertionError(String.format("Crashed test case %d.", i), ex);
            }
        }
    }

    private static void loadTestDir(Path tempDir, List<TestDirent> dirents) throws IOException {
        Util.removeDirent(tempDir);
        Files.createDirectories(tempDir);

        for (TestDirent dirent : dirents) {
            dirent.write(tempDir);
        }
    }

    private static void matchTestDir(Path tempDir, List<TestDirent> dirents) throws IOException {
        for (TestDirent dirent : dirents) {
            String message = dirent.match(tempDir);
            if (message != null) {
                Assertions.fail("Dirent does not match: " + message);
            }
        }
    }

    private static class SyncTestCase {
        public Location source;
        public Location dest;
        public EnumSet<Sync.IdentityCheck> identityChecks;
        public Sync.ConflictBehavior conflictBehavior;

        public List<TestDirent> dirents;

        public SyncTestCase(
                Location source, Location dest,
                EnumSet<Sync.IdentityCheck> identityChecks, Sync.ConflictBehavior conflictBehavior,
                List<TestDirent> dirents) {
            this.source = source;
            this.dest = dest;
            this.identityChecks = identityChecks;
            this.conflictBehavior = conflictBehavior;
            this.dirents = dirents;
        }
    }

    private static class SyncConflictTestCase {
        public Location source;
        public Location dest;
        public EnumSet<Sync.IdentityCheck> identityChecks;
        public Sync.ConflictBehavior conflictBehavior;

        public List<TestDirent> sourceDirents;
        public List<TestDirent> destDirents;
        public List<TestDirent> expectedDirents;

        public SyncConflictTestCase(
                Location source, Location dest,
                EnumSet<Sync.IdentityCheck> identityChecks, Sync.ConflictBehavior conflictBehavior,
                List<TestDirent> sourceDirents,
                List<TestDirent> destDirents,
                List<TestDirent> expectedDirents) {
            this.source = source;
            this.dest = dest;
            this.identityChecks = identityChecks;
            this.conflictBehavior = conflictBehavior;
            this.sourceDirents = sourceDirents;
            this.destDirents = destDirents;
            this.expectedDirents = expectedDirents;
        }
    }

    private abstract static class TestDirent {
        public String relpath;
        public long modTimeMS;

        public TestDirent(String relpath) {
            this.relpath = relpath;
            this.modTimeMS = 0;
        }

        /**
         * Set the mod time and return this.
         */
        public TestDirent setModTime(long modTimeMS) {
            this.modTimeMS = modTimeMS;
            return this;
        }

        public Path getPath(Path baseDir) {
            return Paths.get(baseDir.toString(), this.relpath).toAbsolutePath();
        }

        /**
         * Check if this dirent exists in the base dir.
         * Will return an error message on a bad match, and null on a successful match.
         */
        public String match(Path baseDir) throws IOException {
            Path path = this.getPath(baseDir);

            if (!Util.exists(path)) {
                return String.format("Dirent does not exist: '%s'.", this.relpath);
            }

            if ((this instanceof TestFile) && (this.modTimeMS != Util.getModTime(path))) {
                return String.format("Mod time mismatch for '%s'. Expected: %d, Actual: %d.", this.relpath, this.modTimeMS, Util.getModTime(path));
            }

            String message = this.innerMatch(path);
            if (message == null) {
                return null;
            }

            return String.format("Failed to match '%s': %s.", this.relpath, message);
        }

        public void write(Path baseDir) throws IOException {
            Path path = this.getPath(baseDir);
            this.innerWrite(path);

            if (this instanceof TestFile) {
                Files.setLastModifiedTime(path, FileTime.fromMillis(this.modTimeMS));
            }
        }

        public abstract void innerWrite(Path path) throws IOException;

        public abstract String innerMatch(Path path) throws IOException;
    }

    private static class TestFile extends TestDirent {
        public String contents;

        public TestFile(String relpath, String contents) {
            super(relpath);

            this.contents = contents;
        }

        public void innerWrite(Path path) throws IOException {
            Files.createDirectories(path.getParent());
            Files.write(path, List.of(this.contents));
        }

        public String innerMatch(Path path) throws IOException {
            if (!Util.isFile(path)) {
                return String.format("Dirent is not a file: '%s'.", this.relpath);
            }

            String contents = String.join(System.lineSeparator(), Files.readAllLines(path));
            if (this.contents.equals(contents)) {
                return null;
            }

            return String.format("String contents differ. Expected: '%s', Actual: '%s'.", this.contents, contents);
        }
    }

    private static class TestDir extends TestDirent {
        public TestDir(String relpath) {
            super(relpath);
        }

        public void innerWrite(Path path) throws IOException {
            Files.createDirectories(path);
        }

        public String innerMatch(Path path) throws IOException {
            if (!Util.isDir(path)) {
                return String.format("Dirent is not a dir: '%s'.", this.relpath);
            }

            return null;
        }
    }

    private static class TestLink extends TestDirent {
        public String target;

        public TestLink(String relpath, String target) {
            super(relpath);

            this.target = target;
        }

        public void innerWrite(Path path) throws IOException {
            Files.createDirectories(path.getParent());
            Files.createSymbolicLink(path, Path.of(this.target));
        }

        public String innerMatch(Path path) throws IOException {
            if (!Util.isLink(path)) {
                return String.format("Dirent is not a link: '%s'.", this.relpath);
            }

            String target = Files.readSymbolicLink(path).toString();
            if (this.target.equals(target)) {
                return null;
            }

            return String.format("Link target differ. Expected: '%s', Actual: '%s'.", this.target, target);
        }
    }
}
