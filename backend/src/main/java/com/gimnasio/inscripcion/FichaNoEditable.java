package com.gimnasio.inscripcion;

/** La ficha ya se envió (o la inscripción no existe en este gimnasio): no se puede modificar. */
public class FichaNoEditable extends RuntimeException {

    public FichaNoEditable() {
        super("La ficha ya no se puede modificar");
    }
}
