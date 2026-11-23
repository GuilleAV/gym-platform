package com.gimnasio.plataforma.resolucion;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gimnasio.plataforma.GimnasioActual;

/**
 * Busca en el catálogo ({@code gym_platform}) a qué gimnasio pertenece un dominio.
 *
 * <p>Solo devuelve gimnasios ACTIVOS con el dominio ACTIVO: un gimnasio suspendido o un dominio
 * dado de baja se comportan igual que un dominio desconocido (denegar por defecto).</p>
 *
 * <p>La consulta usa "?" (parámetro): el host nunca se concatena al SQL, así que no hay
 * inyección SQL posible por más raro que venga el header Host.</p>
 */
@Repository
class CatalogoDominios {

    private static final String SQL = """
            SELECT g.id, g.slug, g.nombre, g.zona_horaria
              FROM gimnasio_dominio d
              JOIN gimnasio g ON g.id = d.gimnasio_id
             WHERE d.host = ?
               AND d.activo = TRUE
               AND g.estado = 'ACTIVO'
            """;

    private final JdbcTemplate jdbc;

    CatalogoDominios(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<GimnasioActual> buscarPorHost(String hostNormalizado) {
        List<GimnasioActual> encontrados = jdbc.query(SQL,
                (fila, n) -> new GimnasioActual(
                        fila.getLong("id"),
                        fila.getString("slug"),
                        fila.getString("nombre"),
                        ZoneId.of(fila.getString("zona_horaria"))),
                hostNormalizado);
        // uk_dominio_host garantiza como máximo uno.
        return encontrados.stream().findFirst();
    }
}
