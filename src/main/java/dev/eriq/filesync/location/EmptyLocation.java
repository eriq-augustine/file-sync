package dev.eriq.filesync.location;

import dev.eriq.filesync.location.Location;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A location for testing that never has anything in it (even if requests are made to put things in it).
 */
public class EmptyLocation extends Location {
    public EmptyLocation() {
        super("");
    }

    public void open() throws Exception {
    }

    public void close() throws Exception {
    }

    public String joinPath(String a, String b) {
        return a + "/" + b;
    }

    public List<Location.DirentInfo> listDir(String relpath) throws IOException {
        return new ArrayList<Location.DirentInfo>();
    }

    public void makeDir(String relpath) throws IOException {
    }

    public void removeDir(String relpath) throws IOException {
    }

    public void fetchFile(String relpath, Path tempPath) throws IOException {
        throw new UnsupportedOperationException("Reading an empty location file.");
    }

    public void putFile(Path tempPath, String relpath) throws IOException {
    }

    public void removeFile(String relpath) throws IOException {
    }

    public String readLink(String relpath) throws IOException {
        throw new UnsupportedOperationException("Reading an empty location link.");
    }

    public void createLink(String relpath, String target) throws IOException {
    }

    public void removeLink(String relpath) throws IOException {
    }
}
