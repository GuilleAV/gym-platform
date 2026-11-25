package com.gimnasio.inscripcion;

import java.util.List;

/**
 * Resultado de enviar la ficha.
 *
 * @param estado  ACTIVO (modo SIMPLE) o EN_REVISION (modo COMPLETO); null si hubo errores
 * @param errores qué falta o está mal (vacía si se envió)
 */
public record ResultadoEnvio(String estado, List<String> errores) {

    static ResultadoEnvio conErrores(List<String> errores) {
        return new ResultadoEnvio(null, List.copyOf(errores));
    }

    public boolean enviada() {
        return errores.isEmpty();
    }
}
