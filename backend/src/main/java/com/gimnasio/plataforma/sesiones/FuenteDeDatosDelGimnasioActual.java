package com.gimnasio.plataforma.sesiones;

import java.sql.Connection;
import java.sql.SQLException;

import org.springframework.jdbc.datasource.AbstractDataSource;

import com.gimnasio.plataforma.ContextoGimnasio;
import com.gimnasio.plataforma.conexiones.PoolsPorGimnasio;

/**
 * Un {@link javax.sql.DataSource} que, cada vez que se le pide una conexión, la saca del pool del
 * gimnasio ACTUAL (el del contexto). Si no hay gimnasio en el contexto, falla.
 *
 * <p>Lo usa Spring Session para guardar cada sesión en la base de SU gimnasio (ADR-0002 §1): la
 * misma tabla {@code sesion} existe en gym_001, gym_002..., y cada request escribe en la suya.</p>
 */
class FuenteDeDatosDelGimnasioActual extends AbstractDataSource {

    private final PoolsPorGimnasio pools;

    FuenteDeDatosDelGimnasioActual(PoolsPorGimnasio pools) {
        this.pools = pools;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return pools.para(ContextoGimnasio.actual().id()).getConnection();
    }

    @Override
    public Connection getConnection(String usuario, String clave) throws SQLException {
        throw new UnsupportedOperationException("Las credenciales salen del catálogo, no del llamador");
    }
}
