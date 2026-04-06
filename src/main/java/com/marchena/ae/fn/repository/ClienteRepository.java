package com.marchena.ae.fn.repository;

import com.marchena.ae.fn.model.Cliente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ClienteRepository {

    private static final String SELECT_BY_ID =
            "SELECT [Id], [Nombre], [DNI], [Part1], [Part2], [Part3], [Part4], [Part5] " +
                    "FROM [ae].[ClienteAE] WHERE [Id] = ?";

    public Cliente obtenerPorId(Connection connection, int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                Cliente cliente = new Cliente();
                cliente.setId(rs.getInt("Id"));
                cliente.setNombre(rs.getString("Nombre"));
                cliente.setDni(rs.getString("DNI"));
                cliente.setPart1(rs.getString("Part1"));
                cliente.setPart2(rs.getString("Part2"));
                cliente.setPart3(rs.getString("Part3"));
                cliente.setPart4(rs.getString("Part4"));
                cliente.setPart5(rs.getString("Part5"));

                return cliente;
            }
        }
    }
}