package com.marchena.ae.fn.cripto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;

/**
        * AES-256-GCM Symmetric Encryption Utility.
        *
        * Layout: [ IV (12 bytes) | Ciphertext | Authentication Tag (16 bytes) ]
        */
public class AesEncryptor {

    private static final String ALGORITHM      = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int    KEY_BITS       = 256;
    private static final int    IV_BYTES       = 12;
    private static final int    TAG_BITS       = 128;

    // ThreadLocal para reutilizar Cipher en entornos de alto rendimiento (Azure Functions)
    private static final ThreadLocal<Cipher> CIPHER_THREAD_LOCAL =
            ThreadLocal.withInitial(() -> {
                try {
                    return Cipher.getInstance(TRANSFORMATION);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to initialize AES/GCM Cipher", e);
                }
            });

    // ------------------------------------------------------------------ //
    //  Key and IV generation                                              //
    // ------------------------------------------------------------------ //

    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(KEY_BITS, SecureRandom.getInstanceStrong());
        return keyGenerator.generateKey();
    }

    public static byte[] generateIv() throws NoSuchAlgorithmException {
        byte[] iv = new byte[IV_BYTES];
        SecureRandom.getInstanceStrong().nextBytes(iv);
        return iv;
    }

    // ------------------------------------------------------------------ //
    //  Encryption                                                         //
    // ------------------------------------------------------------------ //

    public static byte[] encrypt(byte[] plaintext, SecretKey key) throws Exception {
        Objects.requireNonNull(plaintext, "Plaintext cannot be null");
        Objects.requireNonNull(key, "SecretKey cannot be null");

        byte[] iv = generateIv();

        Cipher cipher = CIPHER_THREAD_LOCAL.get();
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

        byte[] ciphertextWithTag = cipher.doFinal(plaintext);

        // [IV | Ciphertext + Tag]
        byte[] result = new byte[IV_BYTES + ciphertextWithTag.length];
        System.arraycopy(iv, 0, result, 0, IV_BYTES);
        System.arraycopy(ciphertextWithTag, 0, result, IV_BYTES, ciphertextWithTag.length);

        return result;
    }

    // ------------------------------------------------------------------ //
    //  Decryption                                                         //
    // ------------------------------------------------------------------ //

    public static byte[] decrypt(byte[] ivAndCiphertext, byte[] keyBytes) throws Exception {
        Objects.requireNonNull(ivAndCiphertext, "Encrypted data cannot be null");
        Objects.requireNonNull(keyBytes, "Key bytes cannot be null");

        if (ivAndCiphertext.length < IV_BYTES + 16) {  // IV + mínimo tag + algo de datos
            throw new IllegalArgumentException("Invalid encrypted data: too short");
        }

        // Extraer IV
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(ivAndCiphertext, 0, iv, 0, IV_BYTES);

        // El resto es ciphertext + tag
        byte[] ciphertextWithTag = new byte[ivAndCiphertext.length - IV_BYTES];
        System.arraycopy(ivAndCiphertext, IV_BYTES, ciphertextWithTag, 0, ciphertextWithTag.length);

        SecretKey key = new SecretKeySpec(keyBytes, ALGORITHM);

        Cipher cipher = CIPHER_THREAD_LOCAL.get();
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

        return cipher.doFinal(ciphertextWithTag);
    }
}
