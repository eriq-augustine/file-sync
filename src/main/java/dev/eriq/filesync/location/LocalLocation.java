package dev.eriq.filesync.location;

import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.util.DirentUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * A location that exists on the current machine.
 */
public class LocalLocation extends Location {
    public LocalLocation(Path baseDir) {
        super(baseDir.toAbsolutePath().toString());
    }

    public void open() throws Exception {
        Path basePath = Path.of(baseDir);

        if (!DirentUtils.exists(basePath)) {
            Files.createDirectories(basePath);
        }

        if (!DirentUtils.isDir(basePath)) {
            throw new FileNotFoundException(String.format("Base directory for local location exists, but is not a directory: '%s'.", basePath.toString()));
        }
    }

    public void close() throws Exception {
    }

    public String joinPath(String a, String b) {
        return Paths.get(a, b).toString();
    }

    public List<Location.DirentInfo> listDir(String relpath) throws IOException {
        Path path = Paths.get(baseDir, relpath).toAbsolutePath();

        List<Location.DirentInfo> dirents = new ArrayList<Location.DirentInfo>();
        for (File dirent : path.toFile().listFiles()) {
            Location.DirentInfo.Type type = Location.DirentInfo.Type.OTHER;
            if (DirentUtils.isLink(dirent.toPath())) {
                type = Location.DirentInfo.Type.SYMLINK;
            } else if (dirent.isFile()) {
                type = Location.DirentInfo.Type.FILE;
            } else if (dirent.isDirectory()) {
                type = Location.DirentInfo.Type.DIR;
            }

            dirents.add(new Location.DirentInfo(dirent.getName(), type, dirent.length(), dirent.lastModified()));
        }

        return dirents;
    }

    public void makeDir(String relpath) throws IOException {
        Files.createDirectory(Paths.get(baseDir, relpath));
    }

    public void removeDir(String relpath) throws IOException {
        Files.delete(Paths.get(baseDir, relpath));
    }

    public void fetchFile(String relpath, Path tempPath) throws IOException {
        copy(Paths.get(baseDir, relpath), tempPath);
    }

    public void putFile(Path tempPath, String relpath) throws IOException {
        copy(tempPath, Paths.get(baseDir, relpath));
    }

    public void removeFile(String relpath) throws IOException {
        Files.delete(Paths.get(baseDir, relpath));
    }

    public String readLink(String relpath) throws IOException {
        return Files.readSymbolicLink(Paths.get(baseDir, relpath)).toString();
    }

    public void createLink(String relpath, String target) throws IOException {
        Files.createSymbolicLink(Paths.get(baseDir, relpath), Path.of(target));
    }

    public void removeLink(String relpath) throws IOException {
        Files.delete(Paths.get(baseDir, relpath));
    }

    private void copy(Path source, Path dest) throws IOException {
        source = source.toAbsolutePath();
        dest = dest.toAbsolutePath();

        if (!DirentUtils.exists(source)) {
            throw new FileNotFoundException(String.format("Cannot find local file: '%s'.", source.toString()));
        }

        if (DirentUtils.exists(dest)) {
            DirentUtils.removeDirent(dest);
        }

        // Ensure the dest dir exists.
        Files.createDirectories(dest.getParent());

        if (DirentUtils.isDir(source)) {
            Files.createDirectory(dest);
        } else {
            Files.copy(source, dest, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }
}
