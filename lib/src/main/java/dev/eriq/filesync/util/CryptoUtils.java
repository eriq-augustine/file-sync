package dev.eriq.filesync.util;

import dev.eriq.filesync.errors.BadInputError;
import dev.eriq.filesync.errors.FileSyncError;
import dev.eriq.filesync.errors.IOError;

import net.schmizz.sshj.common.Buffer;
import net.schmizz.sshj.common.KeyType;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jcajce.spec.OpenSSHPublicKeySpec;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.security.Key;
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

    public static PrivateKey parsePrivateKey(String text) {
        Object rawPrasedObject = null;
        try {
            PEMParser pemParser = new PEMParser(new StringReader(text));
            rawPrasedObject = pemParser.readObject();
        } catch (IOException ex) {
            throw new BadInputError("Unable to parse private key.", ex);
        }

        if (!(rawPrasedObject instanceof PrivateKeyInfo)) {
            throw new BadInputError("Input is not a private key in the PKCS#8 format.");
        }

        try {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            return converter.getPrivateKey((PrivateKeyInfo)rawPrasedObject);
        } catch (org.bouncycastle.openssl.PEMException ex) {
            throw new FileSyncError("Unable to convert private key.", ex);
        }
    }

    public static PublicKey parsePublicKey(String text) {
        String[] parts = text.replace("\\s+", " ").split(" ", 3);

        if (parts.length == 2) {
            throw new BadInputError("Public key is not properly formatted.");
        }

        String content = parts[0];
        if (parts.length > 1) {
            content = parts[1];
        }

        OpenSSHPublicKeySpec keySpec = null;
        try {
            AsymmetricKeyParameter asymmetricKeyParameter = OpenSSHPublicKeyUtil.parsePublicKey(Base64.getDecoder().decode(content));
            keySpec = new OpenSSHPublicKeySpec(OpenSSHPublicKeyUtil.encodePublicKey(asymmetricKeyParameter));
        } catch (IOException ex) {
            throw new FileSyncError("Unable to parse public key.", ex);
        }

        String algorithm = null;
        switch (keySpec.getType()) {
            case "ssh-rsa":
                algorithm = "RSA";
                break;
            default:
                throw new FileSyncError(String.format("Unable to determine public key algorithm: '%s'.", keySpec.getType()));
        }

        try {
            return KeyFactory.getInstance(algorithm).generatePublic(keySpec);
        } catch (java.security.NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException ex) {
            throw new FileSyncError("Unable to parse public key.", ex);
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
            throw new FileSyncError(String.format("Could not find key algorithm: '%s'.", algorithm), ex);
        }

        keyPairGenerator.initialize(size);

        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Serialize a key using standard Java serialization.
     * Sibling to base64ToKey().
     */
    public static String keyToBase64(Key key) {
        try (
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)
        ) {
            objectStream.writeObject(key);
            byte[] bytes = byteStream.toByteArray();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException ex) {
            throw new IOError("Failed to serialize key.", ex);
        }
    }

    /**
     * Deserialize a key using standard Java serialization.
     * Sibling to keyToBase64().
     */
    public static Key base64ToKey(String text) {
        try (
            ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(text));
            ObjectInputStream objectStream = new ObjectInputStream(byteStream)
        ) {
            Object object = objectStream.readObject();
            return (Key)object;
        } catch (IOException | ClassNotFoundException ex) {
            throw new IOError("Failed to deserialize key.", ex);
        }
    }
}
