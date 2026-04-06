package com.marchena.ae.fn.service;

import com.marchena.ae.fn.config.JdbcConnectionProvider;
import com.marchena.ae.fn.model.Cliente;
import com.marchena.ae.fn.model.ClienteResponse;
import com.marchena.ae.fn.repository.ClienteRepository;
import com.marchena.ae.fn.util.ClienteMapper;

import java.sql.Connection;
import java.sql.SQLException;

public class ClienteService {

    private final ClienteRepository clienteRepository = new ClienteRepository();

    public ClienteResponse obtenerClientePorId(int id) throws SQLException {
        try (Connection connection = JdbcConnectionProvider.getConnection()) {
            Cliente entity = clienteRepository.obtenerPorId(connection, id);
            return ClienteMapper.toResponse(entity);
        }
    }
}