package com.gimnasio.plataforma;

import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

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

    /**
     * Ejecuta la operación en UNA transacción de la base del gimnasio actual: o se guarda todo, o nada.
     *
     * <p>Todo lo que use {@link #jdbc()} adentro (también desde otros servicios) participa de la misma
     * transacción, porque Spring ata la conexión al hilo mientras dura. Si la operación lanza una
     * excepción, se deshace todo (rollback).</p>
     *
     * <p><b>Ojo:</b> no mandar correos ni llamar servicios externos adentro: si después hay rollback, el
     * correo ya salió. Primero se confirma la transacción y después se avisa.</p>
     */
    public <T> T enTransaccion(Supplier<T> operacion) {
        DataSource base = pools.para(ContextoGimnasio.actual().id());
        return new TransactionTemplate(new DataSourceTransactionManager(base)).execute(estado -> operacion.get());
    }
}
