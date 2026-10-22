package dev.eriq.filesync.location;

import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.util.CryptoUtils;
import dev.eriq.filesync.util.StringUtils;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper;
import net.schmizz.sshj.xfer.FileSystemFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * A location that is accessible via an SSH connection.
 */
public class SSHLocation extends Location {
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
    private PrivateKey privateKey;
    private PublicKey publicKey;

    private SSHClient sshClient;
    private SFTPClient sftpClient;

    /**
     * Create an SSHLocation.
     *
     * Either a password or (private and public keys) are required.
     */
    public SSHLocation(String hostname, int port, String username, String password, PrivateKey privateKey, PublicKey publicKey, String baseDir) {
        super(baseDir);

        username = StringUtils.nullEmptyString(username);
        if (username == null) {
            throw new IllegalArgumentException("No username provided.");
        }

        password = StringUtils.nullEmptyString(password);
        if ((password == null) && ((privateKey == null) || (publicKey == null))) {
            throw new IllegalArgumentException("Insufficient authentication information provided. Either a password or a private/public key pair are required.");
        }

        this.hostname = hostname;
        this.port = port;
        this.username = username;

        this.password = password;
        this.privateKey = privateKey;
        this.publicKey = publicKey;

        sshClient = null;
        sftpClient = null;
    }

    /**
     * Convenience constructor when using password authentication.
     */
    public static SSHLocation withPassword(String hostname, int port, String username, String password, String baseDir) {
        return new SSHLocation(hostname, port, username, password, null, null, baseDir);
    }

    /**
     * Convenience constructor when using key authentication from string keys.
     */
    public static SSHLocation withKeys(String hostname, int port, String username, String privateKey, String publicKey, String baseDir) {
        return new SSHLocation(hostname, port, username, null, CryptoUtils.parsePrivateKey(privateKey), CryptoUtils.parsePublicKey(publicKey), baseDir);
    }

    /**
     * Convenience constructor when using key authentication from keys.
     */
    public static SSHLocation withKeys(String hostname, int port, String username, PrivateKey privateKey, PublicKey publicKey, String baseDir) {
        return new SSHLocation(hostname, port, username, null, privateKey, publicKey, baseDir);
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

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
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
            throw new IllegalArgumentException(String.format("SSH location text does not contain a '@': '%s'.", text));
        }

        String[] userParts = connectionParts[0].split(":", 2);
        if (userParts.length == 1) {
            throw new IllegalArgumentException(String.format("SSH location text does have both a username and password: '%s'.", text));
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

    public void open() throws Exception {
        // Check for already opened.
        if (sshClient != null) {
            return;
        }

        sshClient = new SSHClient();

        // Trust all hosts.
        sshClient.addHostKeyVerifier(new PromiscuousVerifier());

        sshClient.connect(hostname, port);

        // TEST - Put code around auth to make a clean auth error.
        if (password != null) {
            sshClient.authPassword(username, password);
        } else {
            KeyPairWrapper keys = new KeyPairWrapper(publicKey, privateKey);
            sshClient.authPublickey(username, keys);
        }

        sftpClient = sshClient.newSFTPClient();
    }

    public void close() throws Exception {
        // Check for already closed.
        if (sshClient == null) {
            return;
        }

        if (sftpClient != null) {
            sftpClient.close();
            sftpClient = null;
        }

        if (sshClient != null) {
            sshClient.disconnect();
            sshClient = null;
        }
    }

    public String joinPath(String a, String b) {
        return a + PATH_SEP + b;
    }

    public List<Location.DirentInfo> listDir(String relpath) throws IOException {
        String path = joinPath(baseDir, relpath);

        List<Location.DirentInfo> dirents = new ArrayList<Location.DirentInfo>();
        for (RemoteResourceInfo dirent : sftpClient.ls(path)) {
            FileAttributes attributes = dirent.getAttributes();

            Location.DirentInfo.Type type = Location.DirentInfo.Type.OTHER;
            switch (attributes.getType()) {
                case DIRECTORY:
                    type = Location.DirentInfo.Type.DIR;
                    break;
                case REGULAR:
                    type = Location.DirentInfo.Type.FILE;
                    break;
                case SYMLINK:
                    type = Location.DirentInfo.Type.SYMLINK;
                    break;
            }

            dirents.add(new Location.DirentInfo(dirent.getName(), type, attributes.getSize(), attributes.getMtime()));
        }

        return dirents;
    }

    public void makeDir(String relpath) throws IOException {
        String path = joinPath(baseDir, relpath);
        sftpClient.mkdirs(path);
    }

    public void removeDir(String relpath) throws IOException {
        String path = joinPath(baseDir, relpath);
        sftpClient.rmdir(path);
    }

    public void fetchFile(String relpath, Path tempPath) throws IOException {
        String path = joinPath(baseDir, relpath);
        sftpClient.get(path, new FileSystemFile(tempPath.toString()));
    }

    public void putFile(Path tempPath, String relpath) throws IOException {
        String path = joinPath(baseDir, relpath);
        sftpClient.put(new FileSystemFile(tempPath.toString()), path);
    }

    public void removeFile(String relpath) throws IOException {
        String path = joinPath(baseDir, relpath);
        sftpClient.rm(path);
    }

    public String readLink(String relpath) throws IOException {
        String path = joinPath(baseDir, relpath);
        return sftpClient.readlink(path);
    }

    public void createLink(String relpath, String target) throws IOException {
        String path = joinPath(baseDir, relpath);
        // Note that the order is a bit off because of an old upstream bug.
        // See: https://github.com/hierynomus/sshj/issues/144
        sftpClient.symlink(target, path);
    }

    public void removeLink(String relpath) throws IOException {
        String path = joinPath(baseDir, relpath);
        sftpClient.rm(path);
    }
}
