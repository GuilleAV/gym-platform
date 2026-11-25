package com.gimnasio.inscripcion;

import java.time.Instant;

/** Una fila de la lista de inscripciones que ve el personal. */
public record ResumenInscripcion(long id, String nombre, String apellido, String email, String celular, String estado,
        String origen, Instant creadaEn) {
}
