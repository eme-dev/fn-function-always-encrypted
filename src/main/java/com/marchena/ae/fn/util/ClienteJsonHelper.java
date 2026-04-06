package com.marchena.ae.fn.util;

import com.marchena.ae.fn.model.Cliente;

public final class ClienteJsonHelper {

    private ClienteJsonHelper() {
    }

    public static String reconstruirJson(Cliente cliente) {
        if (cliente == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        if (cliente.getPart1() != null) {
            sb.append(cliente.getPart1());
        }
        if (cliente.getPart2() != null) {
            sb.append(cliente.getPart2());
        }
        if (cliente.getPart3() != null) {
            sb.append(cliente.getPart3());
        }
        if (cliente.getPart4() != null) {
            sb.append(cliente.getPart4());
        }
        if (cliente.getPart5() != null) {
            sb.append(cliente.getPart5());
        }

        return sb.toString();
    }
}
