package com.empresa.security.example;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.keys.cryptography.models.UnwrapResult;
import com.azure.security.keyvault.keys.cryptography.models.WrapResult;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Ejemplo minimo de envelope encryption:
 * - Genera una DEK AES-256 temporal.
 * - Cifra datos con AES-GCM.
 * - Envuelve la DEK con una KEK en Azure Key Vault.
 * - Permite recuperar la DEK y descifrar.
 */
public final class EnvelopeEncryptionExample {

    private static final int AES_KEY_SIZE_BITS = 256;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private EnvelopeEncryptionExample() {
    }

    public static void main(String[] args) throws Exception {
        //String keyId = System.getenv("AZURE_KEY_ID");
        String keyId ="https://kv-crypto-fn-east.vault.azure.net/keys/kek-json-dev/f9b1ba6807c1453ea1d94c8b2662eab9";
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalStateException("Defina la variable de entorno AZURE_KEY_ID con el identificador versionado de la KEK.");
        }

        TokenCredential credential = new DefaultAzureCredentialBuilder().build();
        CryptographyClient cryptoClient = new CryptographyClientBuilder()
                .credential(credential)
                .keyIdentifier(keyId)
                .buildClient();

        byte[] plaintext = "dato altamente sensible".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "dominio=clientes".getBytes(StandardCharsets.UTF_8);

        SecretKey dek = generateDek();
        byte[] dekBytes = dek.getEncoded();

        try {
            EncryptedPayload encryptedPayload = encrypt(plaintext, dek, aad);
            WrapResult wrapResult = cryptoClient.wrapKey(KeyWrapAlgorithm.RSA_OAEP_256, dekBytes);

            EncryptedPackage pkg = new EncryptedPackage(
                    Base64.getEncoder().encodeToString(encryptedPayload.getCiphertext()),
                    Base64.getEncoder().encodeToString(encryptedPayload.getIv()),
                    Base64.getEncoder().encodeToString(wrapResult.getEncryptedKey()),
                    keyId,
                    "AES-256-GCM"
            );

            System.out.println("Paquete cifrado:");
            System.out.println("ciphertext=" + pkg.getCiphertextBase64());
            System.out.println("iv=" + pkg.getIvBase64());
            System.out.println("wrappedDek=" + pkg.getWrappedDekBase64());
            System.out.println("kekKeyId=" + pkg.getKekKeyId());

            byte[] wrappedDekBytes = Base64.getDecoder().decode(pkg.getWrappedDekBase64());
            UnwrapResult unwrapResult = cryptoClient.unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, wrappedDekBytes);
            byte[] recoveredDekBytes = unwrapResult.getKey();

            try {
                SecretKey recoveredDek = new SecretKeySpec(recoveredDekBytes, "AES");
                byte[] recoveredPlaintext = decrypt(
                        Base64.getDecoder().decode(pkg.getCiphertextBase64()),
                        Base64.getDecoder().decode(pkg.getIvBase64()),
                        recoveredDek,
                        aad
                );

                System.out.println("descifrado=" + new String(recoveredPlaintext, StandardCharsets.UTF_8));
            } finally {
                Arrays.fill(wrappedDekBytes, (byte) 0);
                Arrays.fill(recoveredDekBytes, (byte) 0);
            }
        } finally {
            Arrays.fill(dekBytes, (byte) 0);
        }
    }

    private static SecretKey generateDek() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(AES_KEY_SIZE_BITS);
        return keyGenerator.generateKey();
    }

    private static EncryptedPayload encrypt(byte[] plaintext, SecretKey dek, byte[] aad) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, dek, spec);
        cipher.updateAAD(aad);

        byte[] ciphertext = cipher.doFinal(plaintext);
        return new EncryptedPayload(ciphertext, iv);
    }

    private static byte[] decrypt(byte[] ciphertext, byte[] iv, SecretKey dek, byte[] aad) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, dek, spec);
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertext);
    }

    private static final class EncryptedPayload {

        private final byte[] ciphertext;
        private final byte[] iv;

        private EncryptedPayload(byte[] ciphertext, byte[] iv) {
            this.ciphertext = ciphertext;
            this.iv = iv;
        }

        private byte[] getCiphertext() {
            return ciphertext;
        }

        private byte[] getIv() {
            return iv;
        }
    }

    private static final class EncryptedPackage {

        private final String ciphertextBase64;
        private final String ivBase64;
        private final String wrappedDekBase64;
        private final String kekKeyId;
        private final String algorithm;

        private EncryptedPackage(
                String ciphertextBase64,
                String ivBase64,
                String wrappedDekBase64,
                String kekKeyId,
                String algorithm
        ) {
            this.ciphertextBase64 = ciphertextBase64;
            this.ivBase64 = ivBase64;
            this.wrappedDekBase64 = wrappedDekBase64;
            this.kekKeyId = kekKeyId;
            this.algorithm = algorithm;
        }

        private String getCiphertextBase64() {
            return ciphertextBase64;
        }

        private String getIvBase64() {
            return ivBase64;
        }

        private String getWrappedDekBase64() {
            return wrappedDekBase64;
        }

        private String getKekKeyId() {
            return kekKeyId;
        }

        @SuppressWarnings("unused")
        private String getAlgorithm() {
            return algorithm;
        }
    }
}
