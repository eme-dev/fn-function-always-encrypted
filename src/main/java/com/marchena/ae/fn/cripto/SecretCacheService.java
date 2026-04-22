package com.marchena.ae.fn.cripto;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.azure.security.keyvault.keys.models.JsonWebKey;
import com.azure.security.keyvault.keys.models.KeyVaultKey;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Cache optimizado para claves públicas RSA usadas en encriptación híbrida.
 */
public final class SecretCacheService {

    private static final Logger log = Logger.getLogger(SecretCacheService.class.getName());

    public static final String ENV_VAULT_URL         = "KEY_VAULT_URL";
    public static final String ENV_CACHE_TTL_MINUTES = "KEY_VAULT_CACHE_TTL_MINUTES";
    private static final int   DEFAULT_TTL_MINUTES   = 30;
    private static final int   MAX_CACHE_SIZE        = 200;

    private static volatile SecretCacheService INSTANCE;

    private final KeyClient keyVaultKeyClient;           // ← Cambiado a KeyClient
    private final LoadingCache<String, PublicKey> publicKeyCache;

    public static SecretCacheService getInstance() {
        if (INSTANCE == null) {
            synchronized (SecretCacheService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SecretCacheService();
                }
            }
        }
        return INSTANCE;
    }

    private SecretCacheService() {
        String vaultUrl = requireEnv(ENV_VAULT_URL);
        int ttlMinutes = parseTtl();

        // Usamos KeyClient porque es un Key (no un Secret)
        this.keyVaultKeyClient = new KeyClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();

        this.publicKeyCache = Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(MAX_CACHE_SIZE)
                .recordStats()
                .build(this::loadAndParsePublicKey);

        log.info(String.format("SecretCacheService (RSA Key) initialized | vault=%s | ttl=%d min",
                vaultUrl, ttlMinutes));
    }

    public PublicKey getPublicKey(String keyName) {
        return publicKeyCache.get(keyName);
    }

    private PublicKey loadAndParsePublicKey(String keyName) {
        try {
            log.info("Cache MISS → Loading RSA Key from Key Vault: " + keyName);

            KeyVaultKey keyVaultKey = keyVaultKeyClient.getKey(keyName);
            JsonWebKey jwk = keyVaultKey.getKey();

            // Obtener la clave pública en formato X509
            byte[] publicKeyBytes = keyVaultKey.getKey().getX();
            if (jwk == null) {
                throw new RuntimeException("Key does not contain JWK material");
            }

            // ← Esta es la línea que estabas buscando
            KeyPair keyPair = jwk.toRsa();                    // toRsa() devuelve KeyPair
            PublicKey publicKey = keyPair.getPublic();

            log.info("Successfully loaded RSA PublicKey: " + keyName +
                    " (Size: " + ((java.security.interfaces.RSAPublicKey) publicKey).getModulus().bitLength() + " bits)");

            return publicKey;


        } catch (Exception e) {
            log.severe("Failed to load/parse RSA Key '" + keyName + "': " + e.getMessage());
            throw new RuntimeException("RSA Key load failed: " + keyName, e);
        }
    }




    public void invalidate(String keyName) {
        publicKeyCache.invalidate(keyName);
        log.info("RSA Key invalidated: " + keyName);
    }

    public void invalidateAll() {
        publicKeyCache.invalidateAll();
        log.info("Full RSA Key cache invalidation.");
    }

    public void logStats() {
        var stats = publicKeyCache.stats();
        log.info(String.format("PublicKey Cache → hitRate=%.3f | hits=%d | misses=%d | evictions=%d",
                stats.hitRate(), stats.hitCount(), stats.missCount(), stats.evictionCount()));
    }

    private static String requireEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Required environment variable not set: " + name);
        }
        return v.trim();
    }

    private static int parseTtl() {
        String raw = System.getenv(ENV_CACHE_TTL_MINUTES);
        if (raw == null || raw.isBlank()) return DEFAULT_TTL_MINUTES;
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : DEFAULT_TTL_MINUTES;
        } catch (Exception e) {
            log.warning("Invalid " + ENV_CACHE_TTL_MINUTES + ", using default " + DEFAULT_TTL_MINUTES);
            return DEFAULT_TTL_MINUTES;
        }
    }
}