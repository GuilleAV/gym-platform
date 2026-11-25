package com.gimnasio.inscripcion;

/** En el mostrador se avisa que el correo ya existe (en el formulario público, no: ADR-0007 §7). */
public class CorreoYaRegistrado extends RuntimeException {

    public CorreoYaRegistrado() {
        super("Ese correo ya está registrado en este gimnasio");
    }
}
