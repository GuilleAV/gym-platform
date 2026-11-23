package com.gimnasio.plataforma.aprovisionamiento;

import java.util.List;

/**
 * Pedido de alta de un gimnasio.
 *
 * @param slug     identificador legible y único (ej. {@code gym-norte}).
 * @param nombre   nombre para mostrar.
 * @param dominios dominios por los que se llega al gimnasio (ej. {@code gym-norte.localhost}).
 */
public record AltaGimnasio(String slug, String nombre, List<String> dominios) {

    public AltaGimnasio {
        dominios = dominios == null ? List.of() : List.copyOf(dominios);
    }
}
