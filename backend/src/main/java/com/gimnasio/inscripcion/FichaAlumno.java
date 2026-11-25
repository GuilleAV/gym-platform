package com.gimnasio.inscripcion;

import java.time.LocalDate;
import java.util.List;

/**
 * La ficha tal como la ve quien la completa: lo que cargó la recepción (no editable acá), lo que falta
 * completar y los documentos a aceptar.
 */
public record FichaAlumno(long inscripcionId, String nombre, String apellido, String email, LocalDate fechaNacimiento,
        boolean esMenor, String estado, DatosFicha datos, List<DocumentoLegal> documentos) {
}
