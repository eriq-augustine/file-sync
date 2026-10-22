package dev.eriq.filesync.location;

import dev.eriq.filesync.Logging;
import dev.eriq.filesync.SSHDockerContainer;
import dev.eriq.filesync.Sync;
import dev.eriq.filesync.location.EmptyLocation;
import dev.eriq.filesync.location.SSHLocation;
import dev.eriq.filesync.util.CryptoUtils;
import dev.eriq.filesync.util.DirentUtils;
import dev.eriq.filesync.util.NetUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;

public class SSHLocationTest {
    @AfterAll
    public static void tearDown() throws IOException {
        SSHDockerContainer.stopAll();
        DirentUtils.removeTempDirs();
    }

    @Test
    public void TestKeyGeneration() throws Exception {
        KeyPair pair = CryptoUtils.generateRSAKeyPair();

        int minLength = 1620;
        int actualLength = CryptoUtils.keyToText(pair.getPrivate()).length();
        Assertions.assertTrue((minLength <= actualLength), String.format("private key length (%d) > %d", actualLength, minLength));

        minLength = 390;
        actualLength = CryptoUtils.keyToText(pair.getPublic()).length();
        Assertions.assertTrue((minLength <= actualLength), String.format("public key length (%d) > %d", actualLength, minLength));
    }

    @Test
    public void TestAuthPassword(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(SSHDockerContainer.isAvailable(), "Docker is not available");

        int port = NetUtils.findAvailablePort();

        SSHLocation location = SSHLocation.withPassword(
                "localhost", port,
                "testuser", "testpass",
                SSHDockerContainer.MOUNT_DIR);

        SSHDockerContainer container = new SSHDockerContainer(location, tempDir);

        try {
            container.start();
            Sync sync = new Sync(new EmptyLocation(), location);
            sync.sync();
        } finally {
            container.stop();
        }
    }

    @Test
    public void TestAuthKey(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(SSHDockerContainer.isAvailable(), "Docker is not available");

        int port = NetUtils.findAvailablePort();
        KeyPair pair = CryptoUtils.generateRSAKeyPair();

        SSHLocation location = SSHLocation.withKeys(
                "localhost", port,
                "testuser",
                pair.getPrivate(), pair.getPublic(),
                SSHDockerContainer.MOUNT_DIR);

        SSHDockerContainer container = new SSHDockerContainer(location, tempDir);

        try {
            container.start();
            Sync sync = new Sync(new EmptyLocation(), location);
            sync.sync();
        } finally {
            container.stop();
        }
    }

    @Test
    public void TestAuthKeyString(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(SSHDockerContainer.isAvailable(), "Docker is not available");

        int port = NetUtils.findAvailablePort();

        String privateKey = String.join("\n",
            "-----BEGIN PRIVATE KEY-----",
            "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",
            "B9vfIG8FVljGN3nvgMu6hAg=",
            "-----END PRIVATE KEY-----"
        ).trim();

        String publicKey = String.join("\n",
            "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQC61prgAFbwt4+GG4CkWx/dH/CD2bGwb3n/n7YK+PdZpRiLYi2oNB/fdxFtFxje2mavUAFFH5z5fzjYXLv2xJn4bXwZxpkF4MxquMGYS35ZDWvbyYim2J5IeM1Q5FREba6jQ5z2zrZvWW7P9jmKYdOmqiHEN4ecj4SxqJnXdwk49oE191MT01YOYVYL9cGelEpxF+GWwPqa1K2+ACxIY7aW7XuHBH0gp5yT5ljeEnIllYbESEmUB1JPDBedCDIg0g0EiiaUcst3xgWO/7Ly52dbvXQz0EKMmIf7sPMWrbMhaJYVTF5BYyS3sU2CNCAHYuVlSutLiGonitkmr5eUoD4omEwwiJiw2PwsoW5vYNRz6Dx5TQr+5WaL2MWRA8H1gsYxdWKzr96wy2iQxc5fcCeyiftB64TwhlvfJ1/RARtQr//4A7nX6Qv6uBoYlOBUd++Q0Yq7PnE9Bw6YTZuSzSKxWkH/1QHnB87GR3GDXd7/HGSMDxtBmZsq7TSPK6Hk3m8= testuser"
        ).trim();

        SSHLocation location = SSHLocation.withKeys(
                "localhost", port,
                "testuser",
                privateKey, publicKey,
                SSHDockerContainer.MOUNT_DIR);

        SSHDockerContainer container = new SSHDockerContainer(location, tempDir);

        try {
            container.start();
            Sync sync = new Sync(new EmptyLocation(), location);
            sync.sync();
        } finally {
            container.stop();
        }
    }
}
