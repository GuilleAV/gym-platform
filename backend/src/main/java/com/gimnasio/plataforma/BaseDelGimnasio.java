package com.gimnasio.plataforma;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.gimnasio.plataforma.conexiones.PoolsPorGimnasio;

/**
 * Puerta de entrada a la base del gimnasio ACTUAL. Es la API que van a usar los módulos de negocio
 * (alumnos, rutinas, caja...).
 *
 * <p>No recibe ningún id de gimnasio: siempre usa el del {@link ContextoGimnasio}, que salió del
 * dominio. Si no hay gimnasio en el contexto, falla. Así es imposible, por diseño, que el código de
 * negocio pida "la base del gimnasio 2" estando en un request del gimnasio 1.</p>
 */
@Component
public class BaseDelGimnasio {

    private final PoolsPorGimnasio pools;

    public BaseDelGimnasio(PoolsPorGimnasio pools) {
        this.pools = pools;
    }

    public JdbcTemplate jdbc() {
        return new JdbcTemplate(pools.para(ContextoGimnasio.actual().id()));
    }
}
