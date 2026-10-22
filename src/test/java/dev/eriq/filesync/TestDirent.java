package dev.eriq.filesync;

import dev.eriq.filesync.util.DirentUtils;

import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.List;

public abstract class TestDirent {
    public String relpath;
    public long modTimeMS;

    public TestDirent(String relpath) {
        this.relpath = relpath;
        modTimeMS = 0;
    }

    /**
        * Set the mod time and return this.
        */
    public TestDirent setModTime(long modTimeMS) {
        this.modTimeMS = modTimeMS;
        return this;
    }

    public Path getPath(Path baseDir) {
        return Paths.get(baseDir.toString(), relpath).toAbsolutePath();
    }

    /**
        * Check if this dirent exists in the base dir.
        * Will return an error message on a bad match, and null on a successful match.
        */
    public String match(Path baseDir) throws IOException {
        Path path = getPath(baseDir);

        if (!DirentUtils.exists(path)) {
            return String.format("Dirent does not exist: '%s'.", relpath);
        }

        if ((this instanceof File) && (modTimeMS != DirentUtils.getModTime(path))) {
            return String.format("Mod time mismatch for '%s'. Expected: %d, Actual: %d.", relpath, modTimeMS, DirentUtils.getModTime(path));
        }

        String message = innerMatch(path);
        if (message == null) {
            return null;
        }

        return String.format("Failed to match '%s': %s.", relpath, message);
    }

    public void write(Path baseDir) throws IOException {
        Path path = getPath(baseDir);
        innerWrite(path);

        if (this instanceof File) {
            Files.setLastModifiedTime(path, FileTime.fromMillis(modTimeMS));
        }
    }

    public abstract void innerWrite(Path path) throws IOException;

    public abstract String innerMatch(Path path) throws IOException;

    public static class File extends TestDirent {
        public String contents;

        public File(String relpath, String contents) {
            super(relpath);

            this.contents = contents;
        }

        public void innerWrite(Path path) throws IOException {
            Files.createDirectories(path.getParent());
            Files.write(path, List.of(contents));
        }

        public String innerMatch(Path path) throws IOException {
            if (!DirentUtils.isFile(path)) {
                return String.format("Dirent is not a file: '%s'.", relpath);
            }

            String contents = String.join(System.lineSeparator(), Files.readAllLines(path));
            if (contents.equals(contents)) {
                return null;
            }

            return String.format("String contents differ. Expected: '%s', Actual: '%s'.", contents, contents);
        }
    }

    public static class Dir extends TestDirent {
        public Dir(String relpath) {
            super(relpath);
        }

        public void innerWrite(Path path) throws IOException {
            Files.createDirectories(path);
        }

        public String innerMatch(Path path) throws IOException {
            if (!DirentUtils.isDir(path)) {
                return String.format("Dirent is not a dir: '%s'.", relpath);
            }

            return null;
        }
    }

    public static class Link extends TestDirent {
        public String target;

        public Link(String relpath, String target) {
            super(relpath);

            this.target = target;
        }

        public void innerWrite(Path path) throws IOException {
            Files.createDirectories(path.getParent());
            Files.createSymbolicLink(path, Path.of(target));
        }

        public String innerMatch(Path path) throws IOException {
            if (!DirentUtils.isLink(path)) {
                return String.format("Dirent is not a link: '%s'.", relpath);
            }

            String target = Files.readSymbolicLink(path).toString();
            if (target.equals(target)) {
                return null;
            }

            return String.format("Link target differ. Expected: '%s', Actual: '%s'.", target, target);
        }
    }

    public static void loadTestDir(Path tempDir, List<TestDirent> dirents) throws IOException {
        DirentUtils.removeDirent(tempDir);
        Files.createDirectories(tempDir);

        for (TestDirent dirent : dirents) {
            dirent.write(tempDir);
        }
    }

    public static void matchTestDir(Path tempDir, List<TestDirent> dirents) throws IOException {
        for (TestDirent dirent : dirents) {
            String message = dirent.match(tempDir);
            if (message != null) {
                Assertions.fail("Dirent does not match: " + message);
            }
        }
    }
}
