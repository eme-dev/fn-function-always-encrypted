package com.marchena.ae.fn.cripto;

import java.security.*;

/**
 * Step 1 — RSA key pair generation.
 *
 * RSA-2048 is the minimum acceptable key size today. For data with a
 * security horizon beyond 10 years, consider RSA-4096 or migrating to
 * elliptic curves (ECDH). OAEP-SHA256 is the recommended padding scheme;
 * avoid raw RSA or PKCS1v1.5 (vulnerable to Bleichenbacher padding-oracle attacks).
 */
public class RsaKeyPairGenerator {

    private static final String ALGORITHM = "RSA";
    private static final int    KEY_SIZE  = 2048; // bits

    /**
     * Generates an RSA key pair ready for use.
     * KeyPairGenerator is thread-safe once initialized.
     */
    public static KeyPair generate() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
        // SecureRandom is selected automatically by the provider (typically /dev/urandom on Linux)
        generator.initialize(KEY_SIZE);
        return generator.generateKeyPair();
    }

    // ---- Convenience accessors ----

    public static PublicKey getPublicKey(KeyPair keyPair)  { return keyPair.getPublic();  }
    public static PrivateKey getPrivateKey(KeyPair keyPair) { return keyPair.getPrivate(); }
}
