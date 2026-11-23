package com.gimnasio.plataforma.conexiones;

/** Una fila de {@code gimnasio_conexion}: dónde está la base de un gimnasio y con qué usuario entrar. */
public record DatosConexion(long gimnasioId, String hostBd, int puertoBd, String nombreBd, String usuarioBd,
        String secretRef, String estado) {
}
