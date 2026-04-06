package com.marchena.ae.fn.model;

import lombok.Data;

@Data
public class ClienteResponse {
    private int id;
    private String nombre;
    private String dni;
    private String datosJson;
}