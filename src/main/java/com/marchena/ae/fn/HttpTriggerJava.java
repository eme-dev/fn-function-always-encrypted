package com.marchena.ae.fn;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import com.marchena.ae.fn.config.JdbcConnectionProvider;

/**
 * Azure Functions with HTTP Trigger.
 */
public class HttpTriggerJava {
    /**
     * This function listens at endpoint "/api/HttpTriggerJava". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/HttpTriggerJava
     * 2. curl {your host}/api/HttpTriggerJava?name=HTTP%20Query
     */
    @FunctionName("HttpTriggerJava")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET, HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context
    ) {
        String nombre = request.getQueryParameters().get("nombre");
        String dni = request.getQueryParameters().get("dni");


        if ((isBlank(nombre) || isBlank(dni)) && request.getBody().isPresent()) {
            String body = request.getBody().orElse("");
            if (isBlank(nombre)) nombre = extractJsonString(body, "nombre");
            if (isBlank(dni)) dni = extractJsonString(body, "dni");
        }

        if (isBlank(nombre) || isBlank(dni)) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Envía nombre y dni (querystring o JSON body: {\"nombre\":\"...\",\"dni\":\"...\"})")
                    .build();
        }

        nombre = nombre.trim();
        dni = dni.trim();

        if (nombre.length() > 100) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("nombre supera 100 caracteres")
                    .build();
        }
        if (dni.length() > 12) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("dni supera 12 caracteres")
                    .build();
        }

        try {
            int id = insertarClienteAE(nombre, dni);
            return request.createResponseBuilder(HttpStatus.OK)
                    .body("OK. Id=" + id)
                    .build();
        } catch (RuntimeException e) {
            context.getLogger().severe("DB error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Database error")
                    .build();
        }
    }

    private int insertarClienteAE(String nombre, String dni) {
        final String sql = "{call ae.InsertarClienteAE(?, ?, ?,?)}";

        try (Connection cn = JdbcConnectionProvider.getConnection();
             CallableStatement cs = cn.prepareCall(sql)) {

            // NVARCHAR => setNString (recomendado con SQL Server + Always Encrypted)
            cs.setString(1, nombre);
            cs.setString(2, dni); // Always Encrypted cifra en el cliente
            cs.setNString(3, generateClienteJsonExactLength(20_000));
            cs.registerOutParameter(4, Types.INTEGER); // Parámetro OUTPUT para IdGenerado
            cs.execute();
            // El SP hace un SELECT al final, así que usamos execute() y leemos ResultSet
            int idGenerado = cs.getInt(4);

            if (cs.wasNull()) {
                throw new SQLException(
                        "El SP retornó NULL en @OutId. " +
                                "Verificar que el INSERT fue exitoso."
                );
            }

            return idGenerado;

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al insertar cliente con SP InsertarClienteAE", e);
        }
    }

    /**
     * Genera un JSON válido con longitud EXACTA (en caracteres Java) igual a targetLength.
     * Rellena con un campo "payload" de 'a' para ajustar el tamaño.
     */
    private String generateClienteJsonExactLength(int targetLength) {
        if (targetLength <= 0) throw new IllegalArgumentException("targetLength debe ser > 0");

        // 1) Armamos el JSON sin el payload (pero con la estructura y el campo payload vacío)
        //    Luego calculamos cuántos caracteres faltan y rellenamos dentro del string del payload.
        String prefix =
                "{"
                        + "\"cliente\":{"
                        +   "\"nombre\":\"Jperez\","
                        +   "\"dni\":\"41373006\","
                        +   "\"contacto\":{"
                        +     "\"email\":\"jperez@mail.com\","
                        +     "\"telefono\":\"999888777\""
                        +   "},"
                        +   "\"direccion\":{"
                        +     "\"pais\":\"PE\","
                        +     "\"ciudad\":\"Lima\","
                        +     "\"linea1\":\"Av. Siempre Viva 123\","
                        +     "\"zip\":\"15001\""
                        +   "}"
                        + "},"
                        + "\"metadata\":{"
                        +   "\"origen\":\"API\","
                        +   "\"version\":1"
                        + "},"
                        + "\"payload\":\"";

        String suffix = "\"}";

        int minLen = prefix.length() + suffix.length(); // payload vacío
        if (targetLength < minLen) {
            throw new IllegalArgumentException(
                    "targetLength demasiado pequeño. Mínimo requerido: " + minLen
            );
        }

        int payloadLen = targetLength - minLen;

        // 2) Relleno: solo letras 'a' (no requiere escape en JSON)
        String payload = repeatChar('a', payloadLen);

        // 3) JSON final exacto
        String json = prefix + payload + suffix;

        // 4) Verificación dura
        if (json.length() != targetLength) {
            throw new IllegalStateException(
                    "Longitud inesperada. Esperada=" + targetLength + " Real=" + json.length()
            );
        }

        return json;
    }

    private  String repeatChar(char c, int count) {
        if (count < 0) throw new IllegalArgumentException("count debe ser >= 0");
        char[] arr = new char[count];
        for (int i = 0; i < count; i++) arr[i] = c;
        return new String(arr);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Parser JSON ultra-simple para strings: busca "key":"value"
     * Sirve para body simple tipo Postman.
     */
    private static String extractJsonString(String json, String key) {
        if (json == null) return null;

        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return null;

        int colon = json.indexOf(':', i + k.length());
        if (colon < 0) return null;

        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;

        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return null;

        return json.substring(firstQuote + 1, secondQuote);
    }
}
