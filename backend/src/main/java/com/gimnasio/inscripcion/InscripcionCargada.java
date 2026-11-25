package com.gimnasio.inscripcion;

/**
 * Resultado de cargar una inscripción en el mostrador.
 *
 * @param invitacionEnviada false si no tiene correo: la ficha se completa en el mostrador
 * @param esMenor           la ficha va a pedir los datos del responsable (ADR-0007 §4)
 */
public record InscripcionCargada(long id, long alumnoId, String estado, boolean invitacionEnviada, boolean esMenor) {
}
