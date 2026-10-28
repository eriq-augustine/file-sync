package dev.eriq.filesync.location;

import dev.eriq.filesync.errors.BadInputError;
import dev.eriq.filesync.errors.FileNotFoundError;
import dev.eriq.filesync.errors.IOError;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.util.DirentUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A location that exists on the current machine.
 */
public class LocalLocation extends Location {
    public LocalLocation(Path baseDir) {
        super(baseDir.toAbsolutePath().toString());
    }

    public static Object fromMap(Map<String, Object> map) {
        return new LocalLocation(Path.of(map.get("base-dir").toString()));
    }

    public void open() {
        Path basePath = Path.of(baseDir);

        if (!DirentUtils.exists(basePath)) {
            try {
                Files.createDirectories(basePath);
            } catch (IOException ex) {
                throw new IOError(String.format("Failed to create base directory for local location: '%s'.", basePath), ex);
            }
        }

        if (!DirentUtils.isDir(basePath)) {
            throw new FileNotFoundError(String.format("Base directory for local location exists, but is not a directory: '%s'.", basePath.toString()));
        }
    }

    public void close() {
    }

    public String joinPath(String a, String b) {
        return Paths.get(a, b).toString();
    }

    public List<Location.DirentInfo> listDir(String relpath) {
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

    public void makeDir(String relpath) {
        Path path = Paths.get(baseDir, relpath);

        try {
            Files.createDirectory(path);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to make local directory: '%s'.", path), ex);
        }
    }

    public void removeDir(String relpath) {
        Path path = Paths.get(baseDir, relpath);

        try {
            Files.delete(path);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to remove local directory: '%s'.", path), ex);
        }
    }

    public void fetchFile(String relpath, Path tempPath) {
        copyFile(Paths.get(baseDir, relpath), tempPath);
    }

    public void putFile(Path tempPath, String relpath) {
        copyFile(tempPath, Paths.get(baseDir, relpath));
    }

    public void removeFile(String relpath) {
        Path path = Paths.get(baseDir, relpath);

        try {
            Files.delete(path);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to remove local file: '%s'.", path), ex);
        }
    }

    public String readLink(String relpath) {
        Path path = Paths.get(baseDir, relpath);

        try {
            return Files.readSymbolicLink(path).toString();
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to read local symlink: '%s'.", path), ex);
        }
    }

    public void createLink(String relpath, String target) {
        Path path = Paths.get(baseDir, relpath);

        try {
            Files.createSymbolicLink(path, Path.of(target));
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to make local symlink: '%s' ('%s').", path, target), ex);
        }
    }

    public void removeLink(String relpath) {
        Path path = Paths.get(baseDir, relpath);

        try {
            Files.delete(path);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to remove local symlink: '%s'.", path), ex);
        }
    }

    private void copyFile(Path source, Path dest) {
        source = source.toAbsolutePath();
        dest = dest.toAbsolutePath();

        if (!DirentUtils.exists(source)) {
            throw new FileNotFoundError(String.format("Cannot find local file: '%s'.", source.toString()));
        }

        if (DirentUtils.exists(dest)) {
            DirentUtils.removeDirent(dest);
        }

        // Ensure the dest dir exists.
        try {
            Files.createDirectories(dest.getParent());
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to create parent directory for local file copy target: '%s'.", dest.getParent()), ex);
        }

        try {
            Files.copy(source, dest, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to copy local file from '%s' to '%s'.", source, dest), ex);
        }
    }
}
