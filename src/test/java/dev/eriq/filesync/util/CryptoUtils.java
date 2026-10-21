package dev.eriq.filesync.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public class CryptoUtils {
    public static final int DEFAULT_KEY_SIZE = 2048;
    public static final String DEFAULT_KEY_ALGORITHM = "RSA";

    public static KeyPair generateRSAKeyPair() {
        return generateRSAKeyPair(DEFAULT_KEY_ALGORITHM, DEFAULT_KEY_SIZE);
    }

    public static KeyPair generateRSAKeyPair(String algorithm, int size) {
        Security.addProvider(new BouncyCastleProvider());

        KeyPairGenerator keyPairGenerator = null;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance(algorithm, "BC");
        } catch (java.security.NoSuchAlgorithmException | java.security.NoSuchProviderException ex) {
            throw new RuntimeException(String.format("Could not find key algorithm: '%s'.", algorithm), ex);
        }

        keyPairGenerator.initialize(size);

        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Convert a private key to a text-based representation.
     * For the output of generateRSAKeyPair(), this will be in PKCS8.
     *
     * This is very fragile (as it does not check the default encoding), and should only be used in testing.
     */
    public static String keyToText(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Convert a public key to a text-based representation.
     * For the output of generateRSAKeyPair(), this will be in X.509.
     *
     * This is very fragile (as it does not check the default encoding), and should only be used in testing.
     */
    public static String keyToText(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}
