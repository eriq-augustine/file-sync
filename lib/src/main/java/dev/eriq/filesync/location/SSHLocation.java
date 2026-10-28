package dev.eriq.filesync.location;

import dev.eriq.filesync.Logging;
import dev.eriq.filesync.errors.AuthError;
import dev.eriq.filesync.errors.BadInputError;
import dev.eriq.filesync.errors.IOError;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.util.CryptoUtils;
import dev.eriq.filesync.util.StringUtils;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
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
import java.util.Map;
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
            throw new BadInputError("No username provided.");
        }

        password = StringUtils.nullEmptyString(password);
        if ((password == null) && ((privateKey == null) || (publicKey == null))) {
            throw new BadInputError("Insufficient authentication information provided. Either a password or a private/public key pair are required.");
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

    protected void serializeToMap(Map<String, Object> data) {
        super.serializeToMap(data);

        data.put("port", port);
        data.put("hostname", hostname);
        data.put("username", username);
        data.put("password", password);

        String outPrivateKey = null;
        if (privateKey != null) {
            outPrivateKey = CryptoUtils.keyToBase64(privateKey);
        }

        String outPublicKey = null;
        if (publicKey != null) {
            outPublicKey = CryptoUtils.keyToBase64(publicKey);
        }

        data.put("private-key", outPrivateKey);
        data.put("public-key", outPublicKey);
    }

    public static Object fromMap(Map<String, Object> data) {
        String hostname = data.get("hostname").toString();
        int port = Integer.parseInt(data.get("port").toString());
        String username = data.get("username").toString();
        String password = StringUtils.nullOrToString(data.get("password"));
        String baseDir = data.get("base-dir").toString();

        PrivateKey privateKey = null;
        String rawPrivateKey = StringUtils.nullOrToString(data.get("private-key"));
        if (rawPrivateKey != null) {
            privateKey = (PrivateKey)CryptoUtils.base64ToKey(rawPrivateKey);
        }

        PublicKey publicKey = null;
        String rawPublicKey = StringUtils.nullOrToString(data.get("public-key"));
        if (rawPublicKey != null) {
            publicKey = (PublicKey)CryptoUtils.base64ToKey(rawPublicKey);
        }

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
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to connect to remote SSH server: '%s:%d'.", hostname, port), ex);
        }
    }

    private void _open() throws IOException {
        // Check for already opened.
        if (sshClient != null) {
            return;
        }

        sshClient = new SSHClient();

        // Trust all hosts.
        sshClient.addHostKeyVerifier(new PromiscuousVerifier());

        sshClient.connect(hostname, port);

        if (password != null) {
            try {
                sshClient.authPassword(username, password);
            } catch (UserAuthException ex) {
                throw new AuthError(String.format("Failed to authenticate user '%s' with password.", username), ex);
            }
        } else {
            KeyPairWrapper keys = new KeyPairWrapper(publicKey, privateKey);

            try {
                sshClient.authPublickey(username, keys);
            } catch (UserAuthException ex) {
                throw new AuthError(String.format("Failed to authenticate user '%s' with keys.", username), ex);
            }
        }

        sftpClient = sshClient.newSFTPClient();
    }

    public void close() {
        try {
            _close();
        } catch (IOException ex) {
            logger.log(java.util.logging.Level.WARNING, String.format("Failed to close connection to remote SSH server: '%s:%d'.", hostname, port), ex);
        }
    }

    public void _close() throws IOException {
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

    public List<Location.DirentInfo> listDir(String relpath) {
        String path = joinPath(baseDir, relpath);

        List<RemoteResourceInfo> rawDirents = null;
        try {
            rawDirents = sftpClient.ls(path);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to list remote directory: '%s'.", path), ex);
        }

        List<Location.DirentInfo> dirents = new ArrayList<Location.DirentInfo>();
        for (RemoteResourceInfo dirent : rawDirents) {
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

    public void makeDir(String relpath) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpClient.mkdirs(path);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to make remote directory: '%s'.", path), ex);
        }
    }

    public void removeDir(String relpath) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpClient.rmdir(path);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to remove remote directory: '%s'.", path), ex);
        }
    }

    public void fetchFile(String relpath, Path tempPath) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpClient.get(path, new FileSystemFile(tempPath.toString()));
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to fetch remote file: '%s'.", path), ex);
        }
    }

    public void putFile(Path tempPath, String relpath) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpClient.put(new FileSystemFile(tempPath.toString()), path);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to put remote file: '%s'.", path), ex);
        }
    }

    public void removeFile(String relpath) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpClient.rm(path);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to remove remote file: '%s'.", path), ex);
        }
    }

    public String readLink(String relpath) {
        String path = joinPath(baseDir, relpath);

        try {
            return sftpClient.readlink(path);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to read remote symlink: '%s'.", path), ex);
        }
    }

    public void createLink(String relpath, String target) {
        String path = joinPath(baseDir, relpath);

        try {
            // Note that the order is a bit off because of an old upstream bug.
            // See: https://github.com/hierynomus/sshj/issues/144
            sftpClient.symlink(target, path);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to create remote symlink: '%s'.", path), ex);
        }
    }

    public void removeLink(String relpath) {
        String path = joinPath(baseDir, relpath);

        try {
            sftpClient.rm(path);
        } catch (IOException ex) {
            throw new IOError(String.format("Failed to remove remote symlink: '%s'.", path), ex);
        }
    }
}
