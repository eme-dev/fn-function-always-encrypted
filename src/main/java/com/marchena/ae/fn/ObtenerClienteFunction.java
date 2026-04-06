package com.marchena.ae.fn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marchena.ae.fn.model.ClienteResponse;
import com.marchena.ae.fn.service.ClienteService;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.sql.SQLException;
import java.util.Optional;

public class ObtenerClienteFunction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ClienteService clienteService = new ClienteService();

    @FunctionName("obtenerCliente")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = "clientes/{id}"
            ) HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext context
    ) {
        context.getLogger().info("Procesando solicitud para obtener cliente con id: " + id);

        try {
            int clienteId = Integer.parseInt(id);

            ClienteResponse response = clienteService.obtenerClientePorId(clienteId);

            if (response == null) {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .header("Content-Type", "application/json")
                        .body("{\"mensaje\":\"Cliente no encontrado\"}")
                        .build();
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(toJson(response))
                    .build();

        } catch (NumberFormatException e) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"mensaje\":\"El id debe ser numérico\"}")
                    .build();

        } catch (SQLException e) {
            context.getLogger().severe("Error SQL al obtener cliente: " + e.getMessage());

            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"mensaje\":\"Error al consultar la base de datos\"}")
                    .build();

        } catch (Exception e) {
            context.getLogger().severe("Error inesperado: " + e.getMessage());

            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"mensaje\":\"Error interno del servidor\"}")
                    .build();
        }
    }

    private String toJson(Object object) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(object);
    }
}