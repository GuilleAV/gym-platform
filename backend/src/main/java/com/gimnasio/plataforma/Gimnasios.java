package com.gimnasio.plataforma;

import java.time.ZoneId;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Consulta de gimnasios del catálogo para OTROS módulos (API pública de plataforma).
 *
 * <p>Uso típico: un job o un arranque que necesita "entrar" a un gimnasio por su slug con
 * {@link ContextoGimnasio#ejecutarPara}. Nunca se usa con datos que mande un cliente HTTP: en un
 * request, el gimnasio sale del dominio.</p>
 */
@Component
public class Gimnasios {

    private final JdbcTemplate catalogo;

    public Gimnasios(JdbcTemplate catalogo) {
        this.catalogo = catalogo;
    }

    public Optional<GimnasioActual> activoPorSlug(String slug) {
        return catalogo.query("SELECT id, slug, nombre, zona_horaria FROM gimnasio WHERE slug = ? AND estado = 'ACTIVO'",
                (fila, n) -> new GimnasioActual(fila.getLong("id"), fila.getString("slug"), fila.getString("nombre"),
                        ZoneId.of(fila.getString("zona_horaria"))),
                slug).stream().findFirst();
    }
}
