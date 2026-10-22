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

        // Make a new location that points to the mounted directory and available port.
        SSHLocation location = new SSHLocation(
                "localhost", port,
                "testuser", "testpass",
                null, null,
                SSHDockerContainer.MOUNT_DIR);

        // Create a new container and mount the empty temp dir to it.
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
