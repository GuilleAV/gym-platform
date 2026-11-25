package com.gimnasio.notificaciones;

import java.util.Objects;

/**
 * Un correo de texto a enviar.
 *
 * @param para   destinatario
 * @param asunto asunto
 * @param texto  cuerpo en texto plano (se lee bien en cualquier cliente y no lo marcan como spam por el HTML)
 */
public record Correo(String para, String asunto, String texto) {

    public Correo {
        Objects.requireNonNull(para, "para");
        Objects.requireNonNull(asunto, "asunto");
        Objects.requireNonNull(texto, "texto");
    }

    /** Nunca imprimir el destinatario ni el cuerpo: pueden tener datos personales o un código de ingreso. */
    @Override
    public String toString() {
        return "Correo[asunto=" + asunto + "]";
    }
}
