package dev.eriq.filesync;

import dev.eriq.filesync.Logging;
import dev.eriq.filesync.TestDirent;
import dev.eriq.filesync.SSHDockerContainer;
import dev.eriq.filesync.Sync;
import dev.eriq.filesync.location.LocalLocation;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.location.SSHLocation;
import dev.eriq.filesync.util.DirentUtils;
import dev.eriq.filesync.util.NetUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.EnumSet;
import java.util.List;

public class SyncTest {
    @AfterAll
    public static void tearDown() {
        SSHDockerContainer.stopAll();
        DirentUtils.removeTempDirs();
    }

    private interface LocationFactory {
        public Location create(Path baseDir);
    }

    private class LocalLocationFactory implements LocationFactory {
        public Location create(Path baseDir) {
            return new LocalLocation(baseDir);
        }
    }

    private class SSHLocationFactory implements LocationFactory {
        public Location create(Path baseDir) {
            Assumptions.assumeTrue(SSHDockerContainer.isAvailable(), "Docker is not available");

            int port = NetUtils.findAvailablePort();

            // Make a new location that points to the mounted directory and available port.
            SSHLocation location = new SSHLocation(
                    "localhost", port,
                    "testuser", "testpass",
                    null, null,
                    SSHDockerContainer.MOUNT_DIR);

            // Create a new container and mount the base dir to it.
            SSHDockerContainer container = new SSHDockerContainer(location, baseDir);
            container.start();

            return location;
        }
    }

    @Test
    public void testSyncSimpleBaseLocal() {
        testSyncSimpleBase(new LocalLocationFactory(), new LocalLocationFactory());
    }

    @Test
    public void testSyncSimpleBaseSSH() {
        testSyncSimpleBase(new SSHLocationFactory(), new SSHLocationFactory());
    }

    private void testSyncSimpleBase(LocationFactory sourceFactory, LocationFactory destFactory) {
        List<List<TestDirent>> testCases = List.of(
            // Empty
            List.of(),

            // Text File
            List.of(
                new TestDirent.File("a.txt", "abc")
            ),

            // Dir
            List.of(
                new TestDirent.Dir("somedir")
            ),

            // Link
            List.of(
                new TestDirent.File("someFile.txt", "abc"),
                new TestDirent.Link("someLink.txt", "someFile.txt")
            ),

            // Link (Broken)
            List.of(
                new TestDirent.Link("someLink.txt", "someFile.txt")
            ),

            // Multiple Dirents
            List.of(
                new TestDirent.File("a.txt", "abc"),
                new TestDirent.File("dir1/b.txt", "123"),
                new TestDirent.File("dir1/dir2/c.txt", "abc123")
            )
        );

        Path stagingTempDir = DirentUtils.getTempDir("staging");
        Path finalOutputDir = DirentUtils.getTempDir("output");
        Path sourceLocationDir = DirentUtils.getTempDir("source");
        Path destLocationDir = DirentUtils.getTempDir("dest");

        for (int i = 0; i < testCases.size(); i++) {
            List<TestDirent> dirents = testCases.get(i);

            try {
                // Clear source and dest dirs.
                DirentUtils.removeDirent(sourceLocationDir);
                DirentUtils.removeDirent(destLocationDir);
                Files.createDirectories(sourceLocationDir);
                Files.createDirectories(destLocationDir);

                // Setup the initial source files.
                TestDirent.loadTestDir(stagingTempDir, dirents);

                // Setup the final output dir.
                DirentUtils.removeDirent(finalOutputDir);
                Files.createDirectories(finalOutputDir);

                Location source = sourceFactory.create(sourceLocationDir);
                Location dest = sourceFactory.create(destLocationDir);

                // Sync files from the staging to the test source.
                Sync sync = new Sync(new LocalLocation(stagingTempDir), source);
                sync.sync();

                // Sync between test case locations.
                sync = new Sync(source, dest, EnumSet.noneOf(Sync.IdentityCheck.class), Sync.ConflictBehavior.SKIP);
                sync.sync();

                // Sync from the test case destination to the final output.
                sync = new Sync(dest, new LocalLocation(finalOutputDir));
                sync.sync();

                // Ensure the final output matches the specification.
                TestDirent.matchTestDir(finalOutputDir, dirents);
            } catch (TestAbortedException ex) {
                // Ignore
                System.out.println(String.format("Skipped test case %d: '%s'.", i, ex.getMessage()));
            } catch (AssertionError ex) {
                throw new AssertionError(String.format("Failed test case %d.", i), ex);
            } catch (Exception ex) {
                throw new AssertionError(String.format("Crashed test case %d.", i), ex);
            } finally {
                SSHDockerContainer.stopAll();
            }
        }
    }

    @Test
    public void testSyncConflicts(@TempDir Path sourceTempDir, @TempDir Path destTempDir) {
        class TestCase {
            public Location source;
            public Location dest;
            public EnumSet<Sync.IdentityCheck> identityChecks;
            public Sync.ConflictBehavior conflictBehavior;

            public List<TestDirent> sourceDirents;
            public List<TestDirent> destDirents;
            public List<TestDirent> expectedDirents;

            public TestCase(
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

        List<TestCase> testCases = List.of(
            // Dirents with matching names (relpaths), but differing other attributes that result in no conflict.
            // This means that the target dirent will be overwritten.

            // !Conflict(!type, name)
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestDirent.File("a.txt", "abc")
                ),
                List.of(
                    new TestDirent.Dir("a.txt")
                ),
                List.of(
                    new TestDirent.File("a.txt", "abc")
                )
            ),
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestDirent.Dir("a.txt")
                ),
                List.of(
                    new TestDirent.File("a.txt", "abc")
                ),
                List.of(
                    new TestDirent.Dir("a.txt")
                )
            ),

            // !Conflict(type, name, !size)
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestDirent.File("a.txt", "abc")
                ),
                List.of(
                    new TestDirent.File("a.txt", "abc".repeat(100))
                ),
                List.of(
                    new TestDirent.File("a.txt", "abc")
                )
            ),

            // !Conflict(type, name, !modtime)
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(0)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(123)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(0)
                )
            ),

            // !Conflict(type, name, size, !modtime)
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE, Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(0)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(123)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(0)
                )
            ),

            // !Conflict(type, name, !size, modtime)
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE, Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(0)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "abc".repeat(100))).setModTime(0)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(0)
                )
            ),

            // !Conflict(type, name, !size, !modtime)
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE, Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(0)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "abc".repeat(100))).setModTime(123)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(0)
                )
            ),

            // Conflict(type, name), Skip
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestDirent.File("a.txt", "abc")
                ),
                List.of(
                    new TestDirent.File("a.txt", "ABC")
                ),
                List.of(
                    new TestDirent.File("a.txt", "ABC")
                )
            ),

            // Conflict(type, name), Overwrite
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.OVERWRITE,
                List.of(
                    new TestDirent.File("a.txt", "abc")
                ),
                List.of(
                    new TestDirent.File("a.txt", "ABC")
                ),
                List.of(
                    new TestDirent.File("a.txt", "abc")
                )
            ),

            // Conflict(type, name, size), Skip
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    new TestDirent.File("a.txt", "abc")
                ),
                List.of(
                    new TestDirent.File("a.txt", "ABC")
                ),
                List.of(
                    new TestDirent.File("a.txt", "ABC")
                )
            ),

            // Conflict(type, name, size), Overwrite
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE),
                Sync.ConflictBehavior.OVERWRITE,
                List.of(
                    new TestDirent.File("a.txt", "abc")
                ),
                List.of(
                    new TestDirent.File("a.txt", "ABC")
                ),
                List.of(
                    new TestDirent.File("a.txt", "abc")
                )
            ),

            // Conflict(type, name, modtime), Skip
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(123)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "ABC")).setModTime(123)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "ABC")).setModTime(123)
                )
            ),

            // Conflict(type, name, modtime), Overwrite
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.OVERWRITE,
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(123)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "ABC")).setModTime(123)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(123)
                )
            ),

            // Conflict(type, name, size, modtime), Skip
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE, Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.SKIP,
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(123)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "ABC")).setModTime(123)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "ABC")).setModTime(123)
                )
            ),

            // Conflict(type, name, size, modtime), Overwrite
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.of(Sync.IdentityCheck.SIZE, Sync.IdentityCheck.MODTIME),
                Sync.ConflictBehavior.OVERWRITE,
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(123)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "ABC")).setModTime(123)
                ),
                List.of(
                    (new TestDirent.File("a.txt", "abc")).setModTime(123)
                )
            ),

            // Conflict with recursive target.
            new TestCase(
                new LocalLocation(sourceTempDir),
                new LocalLocation(destTempDir),
                EnumSet.noneOf(Sync.IdentityCheck.class),
                Sync.ConflictBehavior.OVERWRITE,
                List.of(
                    new TestDirent.File("a.txt", "abc")
                ),
                List.of(
                    new TestDirent.Dir("a.txt"),
                    new TestDirent.Dir("a.txt/b.txt")
                ),
                List.of(
                    new TestDirent.File("a.txt", "abc")
                )
            )
        );

        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);

            try {
                // Setup the source and dest dirs.
                TestDirent.loadTestDir(sourceTempDir, testCase.sourceDirents);
                TestDirent.loadTestDir(destTempDir, testCase.destDirents);

                // Sync between test case locations.
                Sync sync = new Sync(testCase.source, testCase.dest, testCase.identityChecks, testCase.conflictBehavior);
                sync.sync();

                // Ensure the final output matches the specification.
                TestDirent.matchTestDir(destTempDir, testCase.expectedDirents);
            } catch (AssertionError ex) {
                throw new AssertionError(String.format("Failed test case %d.", i), ex);
            } catch (Exception ex) {
                throw new AssertionError(String.format("Crashed test case %d.", i), ex);
            }
        }
    }
}
