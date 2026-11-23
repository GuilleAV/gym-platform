package com.gimnasio.plataforma;

import java.time.ZoneId;
import java.util.Objects;

/**
 * El gimnasio al que pertenece el request (o el job) que se está ejecutando.
 *
 * <p>Es un {@code record}: inmutable. Nadie puede "cambiar de gimnasio" a mitad de un request
 * modificando este objeto.</p>
 *
 * @param id          ID global del gimnasio (uso interno: nunca viene del cliente).
 * @param slug        identificador legible, ej. {@code gym-norte}.
 * @param nombre      nombre para mostrar.
 * @param zonaHoraria zona del gimnasio, para fechas y vencimientos (en la base todo es UTC).
 */
public record GimnasioActual(long id, String slug, String nombre, ZoneId zonaHoraria) {

    public GimnasioActual {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(zonaHoraria, "zonaHoraria");
    }
}
