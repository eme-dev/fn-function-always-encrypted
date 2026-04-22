package com.empresa.security.poc.function;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.empresa.security.poc.crypto.EnvelopeEncryptionService;
import com.empresa.security.poc.model.EncryptedEnvelope;

public final class EncryptJsonFunctionExample {

    private static final String ENCRYPTION_ALGORITHM = "AES-256-GCM";

    private EncryptJsonFunctionExample() {
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

        String businessJson = "{\n"
                + "  \"customerId\": \"C-1001\",\n"
                + "  \"documentNumber\": \"45879632\",\n"
                + "  \"amount\": 1500.75,\n"
                + "  \"currency\": \"PEN\"\n"
                + "}";

        EncryptedEnvelope envelope = service.encryptJson(businessJson);

        System.out.println("Guardar este JSON serializado en SQL nvarchar(max):");
        System.out.println("Algoritmo configurado=" + configuredAlgorithm);
        System.out.println("{");
        System.out.println("  \"ciphertext\": \"" + envelope.getCiphertext() + "\",");
        System.out.println("  \"wrappedDek\": \"" + envelope.getWrappedDek() + "\",");
        System.out.println("  \"iv\": \"" + envelope.getIv() + "\"");
        System.out.println("}");
    }
}
