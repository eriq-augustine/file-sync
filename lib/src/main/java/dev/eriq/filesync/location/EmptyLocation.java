package dev.eriq.filesync.location;

import dev.eriq.filesync.errors.NotSupportedError;
import dev.eriq.filesync.location.Location;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A location for testing that never has anything in it (even if requests are made to put things in it).
 */
public class EmptyLocation extends Location {
    public EmptyLocation() {
        super("");
    }

    public static Object fromMap(Map<String, Object> map) {
        return new EmptyLocation();
    }

    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }

        return (this.getClass() == other.getClass());
    }

    public void open() {
    }

    public void close() {
    }

    public String joinPath(String a, String b) {
        return a + "/" + b;
    }

    public List<Location.DirentInfo> listDir(String relpath) {
        return new ArrayList<Location.DirentInfo>();
    }

    public void makeDir(String relpath) {
    }

    public void removeDir(String relpath) {
    }

    public void fetchFile(String relpath, Path tempPath) {
        throw new NotSupportedError("Reading an empty location file.");
    }

    public void putFile(Path tempPath, String relpath) {
    }

    public void removeFile(String relpath) {
    }

    public String readLink(String relpath) {
        throw new NotSupportedError("Reading an empty location link.");
    }

    public void createLink(String relpath, String target) {
    }

    public void removeLink(String relpath) {
    }
}
