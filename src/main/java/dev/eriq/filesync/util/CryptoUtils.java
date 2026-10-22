package dev.eriq.filesync.util;

import net.schmizz.sshj.common.Buffer;
import net.schmizz.sshj.common.KeyType;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jcajce.spec.OpenSSHPublicKeySpec;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.StringReader;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.Base64;

public class CryptoUtils {
    public static final int DEFAULT_KEY_SIZE = 2048;
    public static final String DEFAULT_KEY_ALGORITHM = "RSA";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    // TEST - Make tests for this. Check if other formats work. (openssh-style, pkcs#1 pkcs#8).
    public static PrivateKey parsePrivateKey(String text) {
        Object rawPrasedObject = null;
        try {
            PEMParser pemParser = new PEMParser(new StringReader(text));
            rawPrasedObject = pemParser.readObject();
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Unable to parse private key.", ex);
        }

        if (!(rawPrasedObject instanceof PrivateKeyInfo)) {
            throw new IllegalArgumentException("Input is not a private key in the PKCS#8 format.");
        }

        try {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            return converter.getPrivateKey((PrivateKeyInfo)rawPrasedObject);
        } catch (org.bouncycastle.openssl.PEMException ex) {
            throw new RuntimeException("Unable to convert private key.", ex);
        }
    }

    public static PublicKey parsePublicKey(String text) {
        String[] parts = text.replace("\\s+", " ").split(" ", 3);

        if (parts.length == 2) {
            throw new IllegalArgumentException("Public key is not properly formatted.");
        }

        String content = parts[0];
        if (parts.length > 1) {
            content = parts[1];
        }

        OpenSSHPublicKeySpec keySpec = null;
        try {
            AsymmetricKeyParameter asymmetricKeyParameter = OpenSSHPublicKeyUtil.parsePublicKey(Base64.getDecoder().decode(content));
            keySpec = new OpenSSHPublicKeySpec(OpenSSHPublicKeyUtil.encodePublicKey(asymmetricKeyParameter));
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Unable to parse public key.", ex);
        }

        String algorithm = null;
        switch (keySpec.getType()) {
            case "ssh-rsa":
                algorithm = "RSA";
                break;
            default:
                throw new RuntimeException(String.format("Unable to determine public key algorithm: '%s'.", keySpec.getType()));
        }

        try {
            return KeyFactory.getInstance(algorithm).generatePublic(keySpec);
        } catch (java.security.NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException ex) {
            throw new RuntimeException("Unable to parse public key.", ex);
        }
    }

    private static String cleanKeyText(String text) {
        text = text.replaceAll("-----[^-].+-----", "");
        text = text.replaceAll("\\s+", "");
        return text;
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

    /**
     * Output a public key in OpenSSH style.
     */
    public static String keyToOpenSSH(PublicKey key, String comment) {
        byte[] keyData = (new Buffer.PlainBuffer()).putPublicKey(key).getCompactData();

        String[] parts = {
            KeyType.fromKey(key).toString(),
            Base64.getEncoder().encodeToString(keyData),
            comment
        };

        return String.join(" ", parts);
    }

    public static KeyPair generateRSAKeyPair() {
        return generateRSAKeyPair(DEFAULT_KEY_ALGORITHM, DEFAULT_KEY_SIZE);
    }

    public static KeyPair generateRSAKeyPair(String algorithm, int size) {
        KeyPairGenerator keyPairGenerator = null;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance(algorithm, "BC");
        } catch (java.security.NoSuchAlgorithmException | java.security.NoSuchProviderException ex) {
            throw new RuntimeException(String.format("Could not find key algorithm: '%s'.", algorithm), ex);
        }

        keyPairGenerator.initialize(size);

        return keyPairGenerator.generateKeyPair();
    }
}
