package dev.eriq.filesync.location;

import dev.eriq.filesync.Logging;
import dev.eriq.filesync.errors.AuthError;
import dev.eriq.filesync.errors.BadInputError;
import dev.eriq.filesync.errors.IOError;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.util.DirentUtils;
import dev.eriq.filesync.util.StringUtils;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;

/**
 * A location that is accessible via an SSH connection.
 */
public class SSHLocation extends Location {
    private static final Logger logger = Logging.getLogger(SSHLocation.class.getName());

    public static final int DEFAULT_PORT = 22;

    public static final String DEFAULT_BASE_DIR = ".";

    /**
     * STFP uses slash as a path separator (even when running on Windows).
     */
    public static final String PATH_SEP = "/";

    private int port;
    private String hostname;
    private String username;

    private String password;
    private String privateKey;
    private String publicKey;

    private Session sshSession;
    private ChannelSftp sftpChannel;

    /**
     * Create an SSHLocation.
     *
     * Either a password or (private and public keys) are required.
     */
    public SSHLocation(String hostname, int port, String username, String password, String privateKey, String publicKey, String baseDir) {
        super(baseDir);

        username = StringUtils.nullEmptyString(username);
        if (username == null) {
            throw new BadInputError("No username provided.");
        }

        password = StringUtils.nullEmptyString(password);
        privateKey = StringUtils.nullEmptyString(privateKey);
        publicKey = StringUtils.nullEmptyString(publicKey);

        if (((privateKey == null) && (publicKey != null)) || ((privateKey != null) && (publicKey == null))) {
            throw new BadInputError("If either keys are supplied, both must be present.");
        }

        if ((password == null) && ((privateKey == null) || (publicKey == null))) {
            throw new BadInputError("Insufficient authentication information provided. Either a password or a private/public key pair are required.");
        }

        this.hostname = hostname;
        this.port = port;
        this.username = username;

        this.password = password;
        this.privateKey = privateKey;
        this.publicKey = publicKey;

        sshSession = null;
        sftpChannel = null;
    }

    /**
     * Convenience constructor when using password authentication.
     */
    public static SSHLocation withPassword(String hostname, int port, String username, String password, String baseDir) {
        return new SSHLocation(hostname, port, username, password, null, null, baseDir);
    }

    /**
     * Convenience constructor when using key authentication from keys.
     */
    public static SSHLocation withKeys(String hostname, int port, String username, String privateKey, String publicKey, String baseDir) {
        return new SSHLocation(hostname, port, username, null, privateKey, publicKey, baseDir);
    }

    protected void serializeToMap(Map<String, Object> data) {
        super.serializeToMap(data);

        data.put("port", port);
        data.put("hostname", hostname);
        data.put("username", username);
        data.put("password", password);
        data.put("private-key", privateKey);
        data.put("public-key", publicKey);
    }

    public static Object fromMap(Map<String, Object> data) {
        String hostname = data.get("hostname").toString();
        int port = Integer.parseInt(data.get("port").toString());
        String username = data.get("username").toString();
        String password = StringUtils.nullOrToString(data.get("password"));
        String privateKey = StringUtils.nullOrToString(data.get("private-key"));
        String publicKey = StringUtils.nullOrToString(data.get("public-key"));
        String baseDir = data.get("base-dir").toString();

        return new SSHLocation(hostname, port, username, password, privateKey, publicKey, baseDir);
    }

    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }

        SSHLocation otherLocation = (SSHLocation)other;
        return (
            this.hostname.equals(otherLocation.hostname)
            && (this.port == otherLocation.port)
            && this.username.equals(otherLocation.username)
            && StringUtils.nullOrEquals(this.password, otherLocation.password)
            && StringUtils.nullOrEquals(this.publicKey, otherLocation.publicKey)
            && StringUtils.nullOrEquals(this.privateKey, otherLocation.privateKey)
        );
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    /**
     * Create a location from a string formatted as:
     * `<user>:<password>@<host>[:port][::<path>]`.
     */
    public static SSHLocation fromString(String text) {
        String[] pathParts = text.split("::", 2);

        String baseDir = DEFAULT_BASE_DIR;
        if (pathParts.length == 2) {
            baseDir = pathParts[1];
        }

        String[] connectionParts = pathParts[0].split("@", 2);
        if (connectionParts.length == 1) {
            throw new BadInputError(String.format("SSH location text does not contain a '@': '%s'.", text));
        }

        String[] userParts = connectionParts[0].split(":", 2);
        if (userParts.length == 1) {
            throw new BadInputError(String.format("SSH location text does have both a username and password: '%s'.", text));
        }

        String username = userParts[0];
        String password = userParts[1];

        String[] hostParts = connectionParts[1].split(":", 2);

        String hostname = hostParts[0];

        int port = DEFAULT_PORT;
        if (hostParts.length == 2) {
            port = Integer.parseInt(hostParts[1]);
        }

        return new SSHLocation(hostname, port, username, password, null, null, baseDir);
    }

    public void open() {
        try {
            _open();
        } catch (JSchException ex) {
            throw new IOError(String.format("Failed to connect to remote SSH server: '%s:%d'.", hostname, port), ex);
        }
    }

    private void _open() throws JSchException {
        // Check for already opened.
        if (sshSession != null) {
            return;
        }

        // Allow all hosts.
        JSch.setConfig("StrictHostKeyChecking", "no");

        JSch jsch = new JSch();

        if (privateKey != null) {
            jsch.addIdentity(username, privateKey.getBytes(), publicKey.getBytes(), null);
        }

        sshSession = jsch.getSession(username, hostname, port);

        if (password != null) {
            sshSession.setPassword(password);
        }

        sshSession.connect();

        Channel channel = sshSession.openChannel("sftp");
        channel.connect();
        sftpChannel = (ChannelSftp)channel;
    }

    public void close() {
        try {
            _close();
        } catch (JSchException ex) {
            throw new IOError(String.format("Failed to close connection to remote SSH server: '%s:%d'.", hostname, port), ex);
        }
    }

    public void _close() throws JSchException {
        // Check for already closed.
        if (sshSession == null) {
            return;
        }

        if (sftpChannel != null) {
            sftpChannel.disconnect();
            sftpChannel = null;
        }

        if (sshSession != null) {
            sshSession.disconnect();
            sshSession = null;
        }
    }

    public String joinPath(String a, String b) {
        return a + PATH_SEP + b;
    }

    public List<Location.DirentInfo> listDir(String relpath) {
        String path = joinPath(baseDir, relpath);

        Vector<ChannelSftp.LsEntry> rawDirents = null;
        try {
            rawDirents = sftpChannel.ls(path);
        } catch (SftpException ex) {
            throw new IOError(String.format("Failed to list remote directory: '%s'.", path), ex);
        }

        List<Location.DirentInfo> dirents = new ArrayList<Location.DirentInfo>();
        for (ChannelSftp.LsEntry dirent : rawDirents) {
            String name = dirent.getFilename();
            if (name.equals(".") || name.equals("..")) {
                continue;
            }

            SftpATTRS attributes = dirent.getAttrs();

            Location.DirentInfo.Type type = Location.DirentInfo.Type.OTHER;
            if (attributes.isLink()) {
                type = Location.DirentInfo.Type.SYMLINK;
            } else if (attributes.isDir()) {
                type = Location.DirentInfo.Type.DIR;
            } else if (attributes.isReg()) {
                type = Location.DirentInfo.Type.FILE;
            }

            dirents.add(new Location.DirentInfo(name, type, attributes.getSize(), attributes.getMTime() * 1000));
        }

        return dirents;
    }

    public void makeDir(String relpath) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpChannel.mkdir(path);
        } catch (SftpException ex) {
            throw new IOError(String.format("Failed to make remote directory: '%s'.", path), ex);
        }
    }

    public void removeDir(String relpath) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpChannel.rmdir(path);
        } catch (SftpException ex) {
            throw new IOError(String.format("Failed to remove remote directory: '%s'.", path), ex);
        }
    }

    public void fetchFile(String relpath, Path tempPath) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpChannel.get(path, tempPath.toString());
            SftpATTRS attrs = sftpChannel.lstat(path);
            Files.setLastModifiedTime(tempPath, FileTime.fromMillis(attrs.getMTime() * 1000));
        } catch (SftpException | IOException ex) {
            throw new IOError(String.format("Failed to fetch remote file: '%s'.", path), ex);
        }
    }

    public void putFile(Path tempPath, String relpath) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpChannel.put(tempPath.toString(), path);
            sftpChannel.setMtime(path, (int)(DirentUtils.getModTime(tempPath) / 1000));
        } catch (SftpException ex) {
            throw new IOError(String.format("Failed to put remote file: '%s'.", path), ex);
        }
    }

    public void removeFile(String relpath) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpChannel.rm(path);
        } catch (SftpException ex) {
            throw new IOError(String.format("Failed to remove remote file: '%s'.", path), ex);
        }
    }

    public String readLink(String relpath) {
        String path = joinPath(baseDir, relpath);

        try {
            return sftpChannel.readlink(path);
        } catch (SftpException ex) {
            throw new IOError(String.format("Failed to read remote symlink: '%s'.", path), ex);
        }
    }

    public void createLink(String relpath, String target) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpChannel.symlink(target, path);
        } catch (SftpException ex) {
            throw new IOError(String.format("Failed to create remote symlink: '%s'.", path), ex);
        }
    }

    public void removeLink(String relpath) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpChannel.rm(path);
        } catch (SftpException ex) {
            throw new IOError(String.format("Failed to remove remote symlink: '%s'.", path), ex);
        }
    }
}
