package dev.eriq.filesync.location;

import dev.eriq.filesync.Logging;
import dev.eriq.filesync.util.CryptoUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

public class SSHLocationTest {
    @BeforeAll
    public static void setUp() {
        Logging.init();
    }

    @Test
    public void TestKeyGeneration() throws Exception {
        KeyPair pair = CryptoUtils.generateRSAKeyPair();
        Assertions.assertEquals(1624, CryptoUtils.keyToText(pair.getPrivate()).length(), "private key length");
        Assertions.assertEquals(392, CryptoUtils.keyToText(pair.getPublic()).length(), "public key length");
    }
}
