package com.marchena.ae.fn.cripto;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Step 4 — End-to-end demo of hybrid encryption (RSA + AES-256-GCM).
 *
 * Simulates a full exchange between Alice (sender) and Bob (receiver):
 *   1. Bob generates his RSA key pair and shares his public key.
 *   2. Alice encrypts a message using Bob's public key.
 *   3. The encrypted package travels over an insecure channel.
 *   4. Bob decrypts with his private key and recovers the original message.
 *   5. Round-trip integrity is verified.
 *   6. Tamper detection is tested (GCM tag must reject altered ciphertext).
 *
 * Compile and run (JDK 11+, all class files in the same directory):
 *   javac *.java && java Main
 */
public class Main {

    public static void main(String[] args) throws Exception {

        printBanner("PoC — Hybrid Encryption (RSA-OAEP + AES-256-GCM)");

        // ============================================================
        // PHASE 0: Bob generates his RSA key pair
        // In production this happens once; the private key is stored in
        // a KeyStore or HSM, never in plain text.
        // ============================================================
        System.out.println(">>> PHASE 0: Generating RSA-2048 key pair for Bob...");
        long startTime = System.currentTimeMillis();

        KeyPair bobKeyPair    = RsaKeyPairGenerator.generate();
        PublicKey bobPublicKey  = RsaKeyPairGenerator.getPublicKey(bobKeyPair);
        PrivateKey bobPrivateKey = RsaKeyPairGenerator.getPrivateKey(bobKeyPair);

        System.out.printf("    Generation time  : %d ms%n", elapsed(startTime));
        System.out.println("    Algorithm        : " + bobPublicKey.getAlgorithm());
        System.out.println("    Public key format: " + bobPublicKey.getFormat());
        System.out.println();

        // ============================================================
        // PHASE 1: Alice encrypts a message with Bob's public key
        // ============================================================
        String message =
                "Hi Bob, the secret code for the meeting is: DELTA-7749. " +
                        "This message demonstrates that AES-GCM can encrypt data of any length " +
                        "without performance degradation, unlike pure RSA encryption.";

        System.out.println(">>> PHASE 1: Alice encrypts the message...");
        System.out.printf("    Original message (%d chars):%n", message.length());
        System.out.println("    \"" + message + "\"");
        System.out.println();

        startTime = System.currentTimeMillis();
        String encryptedPackage = HybridEncryptor.encrypt(
                message.getBytes(StandardCharsets.UTF_8),
                bobPublicKey
        );
        System.out.printf("    Encryption time  : %d ms%n", elapsed(startTime));

        // Display the package as it would travel over the network
        String[] parts = encryptedPackage.split(":", 2);
        System.out.println();
        System.out.println("    === ENCRYPTED PACKAGE (transmitted over the channel) ===");
        System.out.println("    [AES key wrapped with RSA-OAEP, Base64]:");
        System.out.println("    " + parts[0]);
        System.out.println();
        System.out.println("    [Data encrypted with AES-GCM (IV + ciphertext + tag), Base64]:");
        System.out.println("    " + parts[1]);
        System.out.println("    ========================================================");
        System.out.println();

        // ============================================================
        // PHASE 2: Bob decrypts the package with his private key
        // ============================================================
        System.out.println(">>> PHASE 2: Bob decrypts the package...");
        startTime = System.currentTimeMillis();

        byte[] decryptedBytes  = HybridEncryptor.decrypt(encryptedPackage, bobPrivateKey);
        String recoveredMessage = new String(decryptedBytes, StandardCharsets.UTF_8);

        System.out.printf("    Decryption time  : %d ms%n", elapsed(startTime));
        System.out.println("    Recovered message:");
        System.out.println("    \"" + recoveredMessage + "\"");
        System.out.println();

        // ============================================================
        // PHASE 3: Round-trip integrity check
        // ============================================================
        System.out.println(">>> PHASE 3: Verifying round-trip integrity...");
        boolean intact = message.equals(recoveredMessage);
        System.out.println("    Original == Recovered: " + (intact ? "PASS" : "FAIL"));
        System.out.println();

        // ============================================================
        // PHASE 4: Tamper detection test
        // Flip a byte in the ciphertext; GCM tag must reject it.
        // ============================================================
        System.out.println(">>> PHASE 4: Tamper detection test...");
        byte[] rawData = Base64.getDecoder().decode(parts[1]);
        rawData[rawData.length - 1] ^= 0xFF; // corrupt last byte (part of the GCM tag)
        String tamperedPackage = parts[0] + ":" + Base64.getEncoder().encodeToString(rawData);

        try {
            HybridEncryptor.decrypt(tamperedPackage, bobPrivateKey);
            System.out.println("    WARNING: Tampering was NOT detected (this should never happen!)");
        } catch (javax.crypto.AEADBadTagException e) {
            System.out.println("    PASS — Tampering detected: " + e.getClass().getSimpleName());
            System.out.println("           The GCM authentication tag rejected the altered ciphertext.");
        } catch (Exception e) {
            System.out.println("    PASS — Tampering detected (general exception): " + e.getMessage());
        }

        System.out.println();
        printBanner("Demo completed successfully.");
    }

    // ---- Utilities ----

    private static long elapsed(long startMs) {
        return System.currentTimeMillis() - startMs;
    }

    private static void printBanner(String text) {
        String border = "=".repeat(text.length() + 6);
        System.out.println(border);
        System.out.println("   " + text + "   ");
        System.out.println(border);
        System.out.println();
    }
}