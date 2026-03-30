package com.marchena.ae.fn.config;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.microsoft.sqlserver.jdbc.SQLServerColumnEncryptionAzureKeyVaultProvider;
import com.microsoft.sqlserver.jdbc.SQLServerColumnEncryptionKeyStoreProvider;
import com.microsoft.sqlserver.jdbc.SQLServerConnection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class JdbcConnectionProvider {

    private static final AtomicBoolean providerRegistered = new AtomicBoolean(false);

    static {
        if (providerRegistered.compareAndSet(false, true)) {
            try {
                TokenCredential credential = new DefaultAzureCredentialBuilder().build();
                Map<String, SQLServerColumnEncryptionKeyStoreProvider> providers = new HashMap<>(1);
                providers.put("AZURE_KEY_VAULT", new SQLServerColumnEncryptionAzureKeyVaultProvider(credential));
                SQLServerConnection.registerColumnEncryptionKeyStoreProviders(providers);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }


    /** Obtiene una conexión del pool */
    public static  Connection getConnection() throws SQLException {
        return DriverManager.getConnection(System.getenv("SQL_CONNECTION_STRING"));
    }
}

