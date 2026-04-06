package com.marchena.ae.fn;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

import com.marchena.ae.fn.model.Cliente;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import com.marchena.ae.fn.config.JdbcConnectionProvider;

/**
 * Azure Functions with HTTP Trigger.
 */
public class HttpTriggerJava {
    private static final int PART_SIZE = 4000;
    private static final int MAX_PARTS = 5;
    private static final int MAX_LENGTH = PART_SIZE * MAX_PARTS;

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
        final String sql = "{call ae.InsertarClienteAE(?, ?, ?,?,?,?,?,?,?)}";

        try (Connection cn = JdbcConnectionProvider.getConnection();
             CallableStatement cs = cn.prepareCall(sql)) {
            String jsonData=generateClienteJsonExactLength(15_000);

            Cliente cliente= new Cliente();
            cliente.setNombre(nombre);
            cliente.setDni(dni);
            assignText(cliente,jsonData);

            // NVARCHAR => setNString (recomendado con SQL Server + Always Encrypted)
            cs.setString(1, nombre);
            cs.setString(2, dni); // Always Encrypted cifra en el cliente
            cs.setNString(3, jsonData); // NVARCHAR(MAX) sin cifrar|
            cs.setNString(4, cliente.getPart1());
            cs.setNString(5, cliente.getPart2());
            cs.setNString(6, cliente.getPart3());
            cs.setNString(7, cliente.getPart4());
            cs.setNString(8, cliente.getPart4());


            cs.registerOutParameter(9, Types.INTEGER); // Parámetro OUTPUT para IdGenerado
            cs.execute();
            // El SP hace un SELECT al final, así que usamos execute() y leemos ResultSet
            int idGenerado = cs.getInt(9);

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

    private static String extraer(String texto, int numeroParte) {
        int inicio = numeroParte * PART_SIZE;

        if (inicio >= texto.length()) {
            return null;
        }

        int fin = Math.min(inicio + PART_SIZE, texto.length());
        return texto.substring(inicio, fin);
    }

    private static void validateCliente(Cliente cliente) {
        if (cliente == null) {
            throw new IllegalArgumentException("El cliente no puede ser null");
        }
    }

    public static void assignText(Cliente cliente, String texto) {
        validateCliente(cliente);

        clearParts(cliente);

        if (texto == null || texto.isEmpty()) {
            return;
        }

        validateLength(texto);

        cliente.setPart1(extractPart(texto, 0));
        cliente.setPart2(extractPart(texto, 1));
        cliente.setPart3(extractPart(texto, 2));
        cliente.setPart4(extractPart(texto, 3));
        cliente.setPart5(extractPart(texto, 4));
    }
    private static void clearParts(Cliente cliente) {
        cliente.setPart1(null);
        cliente.setPart2(null);
        cliente.setPart3(null);
        cliente.setPart4(null);
        cliente.setPart5(null);
    }

    private static void validateLength(String texto) {
        if (texto.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "El texto supera el máximo permitido de " + MAX_LENGTH +
                            " caracteres. Longitud actual: " + texto.length()
            );
        }
    }

    private static String extractPart(String texto, int partIndex) {
        int start = partIndex * PART_SIZE;

        if (start >= texto.length()) {
            return null;
        }

        int end = Math.min(start + PART_SIZE, texto.length());
        return texto.substring(start, end);
    }
}
