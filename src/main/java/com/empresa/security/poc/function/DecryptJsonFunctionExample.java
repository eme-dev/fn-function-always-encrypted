package com.empresa.security.poc.function;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.empresa.security.poc.crypto.EnvelopeEncryptionService;
import com.empresa.security.poc.model.EncryptedEnvelope;

public final class DecryptJsonFunctionExample {

    private static final String ENCRYPTION_ALGORITHM = "AES-256-GCM";

    private DecryptJsonFunctionExample() {
    }

    public static void main(String[] args) throws Exception {
        String keyId = System.getenv("AZURE_KEY_ID");
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalStateException("Defina AZURE_KEY_ID con el identificador versionado de la KEK.");
        }

        TokenCredential credential = new DefaultAzureCredentialBuilder().build();
        CryptographyClient cryptoClient = new CryptographyClientBuilder()
                .credential(credential)
                .keyIdentifier(keyId)
                .buildClient();

        EnvelopeEncryptionService service = new EnvelopeEncryptionService(cryptoClient);

        String configuredAlgorithm = System.getenv("ENCRYPTION_ALGORITHM");
        if (configuredAlgorithm == null || configuredAlgorithm.isBlank()) {
            configuredAlgorithm = ENCRYPTION_ALGORITHM;
        }

        EncryptedEnvelope envelope = new EncryptedEnvelope(
                "REEMPLAZAR_CIPHERTEXT_BASE64",
                "REEMPLAZAR_WRAPPED_DEK_BASE64",
                "REEMPLAZAR_IV_BASE64"
        );

        String json = service.decryptJson(envelope);
        System.out.println("Algoritmo configurado=" + configuredAlgorithm);
        System.out.println("JSON recuperado:");
        System.out.println(json);
    }
}
