package com.marchena.ae.fn.util;

import com.marchena.ae.fn.model.Cliente;
import com.marchena.ae.fn.model.ClienteResponse;

public final class ClienteMapper {

    private ClienteMapper() {
    }

    public static ClienteResponse toResponse(Cliente entity) {
        ClienteResponse response = new ClienteResponse();
        response.setId(entity.getId());
        response.setNombre(entity.getNombre());
        response.setDni(entity.getDni());
        response.setDatosJson(ClienteJsonHelper.reconstruirJson(entity));
        return response;
    }
}
