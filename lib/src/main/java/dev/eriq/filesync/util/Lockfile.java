package dev.eriq.filesync.util;

import dev.eriq.filesync.errors.IOError;

import java.io.File;;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;

public class Lockfile {
    /**
     * What to write in the lock file.
     * Defaults to the process's PID.
     */
    private static String fileContents = null;

    private Path path;
    private boolean deleteOnExit;

    private RandomAccessFile file;
    private FileChannel channel;
    private FileLock lock;

    /**
     * Create a lock file abstraction.
     * Callers should generally not use deleteOnExit (except for testing),
     * as it ruins the purpose of a lock file (inter-process locking).
     */
    public Lockfile(Path path, boolean deleteOnExit) {
        this.path = path.toAbsolutePath();
        this.deleteOnExit = deleteOnExit;

        this.file = null;
        this.channel = null;
        this.lock = null;
    }

    public Lockfile(Path path) {
        this(path, false);
    }

    public static void setFileContents(String text) {
        fileContents = text;
    }

    public void acquire() {
        try {
            _acquire();
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to acquire file lock for: '%s'.", path), ex);
        }
    }

    private void _acquire() throws IOException {
        File baseFile = path.toFile();
        if (deleteOnExit) {
            baseFile.deleteOnExit();
        }

        baseFile.getParentFile().mkdirs();

        file = new RandomAccessFile(baseFile, "rw");
        channel = file.getChannel();

        lock = channel.lock();

        file.writeUTF(String.format("%s\n", getFileContents()));
    }

    public void release() {
        try {
            _release();
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to release file lock for: '%s'.", path), ex);
        }
    }

    private void _release() throws IOException {
        file.setLength(0);

        lock.release();

        channel.close();
        file.close();

        file = null;
        channel = null;
        lock = null;
    }

    private String getFileContents() {
        if (fileContents != null) {
            return fileContents;
        }

        return "" + ProcessHandle.current().pid();
    }
}
