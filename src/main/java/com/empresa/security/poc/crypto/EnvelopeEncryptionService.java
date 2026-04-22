package com.empresa.security.poc.crypto;

import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.keys.cryptography.models.UnwrapResult;
import com.azure.security.keyvault.keys.cryptography.models.WrapResult;
import com.empresa.security.poc.model.EncryptedEnvelope;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class EnvelopeEncryptionService {

    private static final int AES_KEY_SIZE_BITS = 256;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final CryptographyClient cryptoClient;

    public EnvelopeEncryptionService(CryptographyClient cryptoClient) {
        this.cryptoClient = cryptoClient;
    }

    public EncryptedEnvelope encryptJson(String json) throws Exception {
        SecretKey dek = generateDek();
        byte[] dekBytes = dek.getEncoded();

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            byte[] ciphertext = encrypt(json.getBytes(StandardCharsets.UTF_8), dek, iv);
            WrapResult wrapResult = cryptoClient.wrapKey(KeyWrapAlgorithm.RSA_OAEP_256, dekBytes);

            return new EncryptedEnvelope(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(wrapResult.getEncryptedKey()),
                    Base64.getEncoder().encodeToString(iv)
            );
        } finally {
            Arrays.fill(dekBytes, (byte) 0);
        }
    }

    public String decryptJson(EncryptedEnvelope envelope) throws Exception {
        byte[] wrappedDekBytes = Base64.getDecoder().decode(envelope.getWrappedDek());
        UnwrapResult unwrapResult = cryptoClient.unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, wrappedDekBytes);
        byte[] dekBytes = unwrapResult.getKey();

        try {
            SecretKey dek = new SecretKeySpec(dekBytes, "AES");
            byte[] plaintext = decrypt(
                    Base64.getDecoder().decode(envelope.getCiphertext()),
                    dek,
                    Base64.getDecoder().decode(envelope.getIv())
            );
            return new String(plaintext, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(wrappedDekBytes, (byte) 0);
            Arrays.fill(dekBytes, (byte) 0);
        }
    }

    private static SecretKey generateDek() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(AES_KEY_SIZE_BITS);
        return keyGenerator.generateKey();
    }

    private static byte[] encrypt(byte[] plaintext, SecretKey dek, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        return cipher.doFinal(plaintext);
    }

    private static byte[] decrypt(byte[] ciphertext, SecretKey dek, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        return cipher.doFinal(ciphertext);
    }
}
