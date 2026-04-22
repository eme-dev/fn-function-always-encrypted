package com.marchena.ae.fn.cripto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.Objects;

/**
 * Step 3 — Hybrid encryption orchestrator (RSA + AES-256-GCM).
 *
 * Encryption flow:
 *   1. Generate an ephemeral AES-256 key (new one per message).
 *   2. Encrypt the message with that key → AES-GCM produces authenticated ciphertext.
 *   3. Wrap the AES key with the recipient's RSA public key → "encrypted key".
 *   4. Package: { encryptedKey (Base64) SEPARATOR encryptedData (Base64) }.
 *
 * Decryption flow (symmetric inverse):
 *   1. Split the encrypted key and encrypted data from the package.
 *   2. Unwrap the AES key with the recipient's RSA private key.
 *   3. Decrypt the data with the recovered AES key → original plaintext.
 *
 * Package format (text):
 *   BASE64(rsa_wrapped_aes_key):BASE64(iv + aes_ciphertext + gcm_tag)
 *
 * Why RSA-OAEP-SHA256:
 *   - OAEP adds randomness to each encryption, so encrypting the same key twice
 *     yields different ciphertexts (semantic security / IND-CPA).
 *   - SHA-256 as the internal hash is stronger than SHA-1 (deprecated).
 *   - Resistant to padding-oracle attacks (unlike PKCS1v1.5).
 */
public class HybridEncryptor {

    // RSA/ECB/OAEPWithSHA-256AndMGF1Padding: asymmetric encryption with OAEP padding.
    // "ECB" here is a legacy JCA label; no real block-cipher mode exists for RSA.
    private static final String RSA_TRANSFORMATION =
            "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private static final String SEPARATOR = ":";

    // ------------------------------------------------------------------ //
    //  Encryption                                                         //
    // ------------------------------------------------------------------ //

    /**
     * Encrypts a message and returns the hybrid package as a Base64 string.
     *
     * @param plaintext data to encrypt (byte array, any length)
     * @param publicKey RSA public key of the recipient
     * @return          package: "BASE64(wrapped_key):BASE64(encrypted_data)"
     */
    public static String encrypt(byte[] plaintext, PublicKey publicKey) throws Exception {

        Objects.requireNonNull(plaintext, "Plaintext cannot be null");
        Objects.requireNonNull(publicKey, "PublicKey cannot be null");

        if (plaintext.length == 0) {
            throw new IllegalArgumentException("Plaintext cannot be empty");
        }

        // Step 1: Generate ephemeral AES-256 key
        SecretKey aesKey = AesEncryptor.generateKey();

        // Step 2: Encrypt data with AES-GCM
        byte[] encryptedData = AesEncryptor.encrypt(plaintext, aesKey);

        // Step 3: Wrap AES key with RSA-OAEP
        byte[] encryptedKey = wrapAesKey(aesKey.getEncoded(), publicKey);

        // Step 4: Package as Base64
        String base64Key = Base64.getEncoder().encodeToString(encryptedKey);
        String base64Data = Base64.getEncoder().encodeToString(encryptedData);

        return base64Key + SEPARATOR + base64Data;
    }

    // ------------------------------------------------------------------ //
    //  Decryption                                                         //
    // ------------------------------------------------------------------ //

    /**
     * Decrypts a hybrid package produced by {@link #encrypt}.
     *
     * @param encryptedPackage package "BASE64(wrapped_key):BASE64(encrypted_data)"
     * @param privateKey       RSA private key of the recipient
     * @return                 original plaintext
     */
    public static byte[] decrypt(String encryptedPackage, PrivateKey privateKey) throws Exception {

        Objects.requireNonNull(encryptedPackage, "Encrypted package cannot be null");
        Objects.requireNonNull(privateKey, "PrivateKey cannot be null");

        String[] parts = encryptedPackage.split(SEPARATOR, 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid hybrid package format");
        }

        byte[] encryptedKey = Base64.getDecoder().decode(parts[0]);
        byte[] encryptedData = Base64.getDecoder().decode(parts[1]);

        byte[] aesKeyBytes = unwrapAesKey(encryptedKey, privateKey);

        return AesEncryptor.decrypt(encryptedData, aesKeyBytes);
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                    //
    // ------------------------------------------------------------------ //

    /**
     * Wraps (encrypts) the raw AES key bytes using RSA-OAEP.
     * For RSA-2048 the practical limit with OAEP-SHA256 is ~190 bytes —
     * more than enough for a 32-byte AES-256 key.
     */
    private static byte[] wrapAesKey(byte[] aesKeyBytes, PublicKey publicKey) throws Exception {
        Objects.requireNonNull(aesKeyBytes, "AES key bytes cannot be null");

        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);

        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
        );

        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams, SecureRandom.getInstanceStrong());
        return cipher.doFinal(aesKeyBytes);
    }

    /**
     * Unwraps (decrypts) the AES key using the RSA private key.
     */
    private static byte[] unwrapAesKey(byte[] encryptedKey, PrivateKey privateKey) throws Exception {
        Objects.requireNonNull(encryptedKey, "Encrypted key cannot be null");

        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);

        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
        );

        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams, SecureRandom.getInstanceStrong());
        return cipher.doFinal(encryptedKey);
    }
}
