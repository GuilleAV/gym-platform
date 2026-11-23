package com.gimnasio.plataforma.conexiones;

import java.time.Duration;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.gimnasio.plataforma.secretos.ResolutorSecretos;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Un pool de conexiones HikariCP por gimnasio, creado a demanda (ADR-0002 §4).
 *
 * <ul>
 *   <li>El pool de un gimnasio se crea la PRIMERA vez que se necesita, no al arrancar: con 200
 *       gimnasios registrados y 10 activos, solo hay 10 pools.</li>
 *   <li>Cada pool usa el usuario MySQL de SU gimnasio ({@code gym_NNN_app}), que solo tiene permisos
 *       sobre su base: aunque el código se equivocara de pool, MySQL no deja ver otra base.</li>
 *   <li>Máximo 4 conexiones, mínimo 0 (sin uso no ocupa conexiones) y, si no se usa en 30 minutos,
 *       el pool se cierra y se borra de la caché (Caffeine).</li>
 * </ul>
 */
@Component
public class PoolsPorGimnasio implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(PoolsPorGimnasio.class);

    static final int MAXIMO_CONEXIONES = 4;
    static final Duration INACTIVIDAD_PARA_CERRAR = Duration.ofMinutes(30);

    private final CatalogoConexiones catalogo;
    private final ResolutorSecretos secretos;
    private final PropiedadesBd bd;

    private final Cache<Long, HikariDataSource> pools = Caffeine.newBuilder()
            .expireAfterAccess(INACTIVIDAD_PARA_CERRAR)
            .removalListener((Long gimnasioId, HikariDataSource pool, RemovalCause causa) -> cerrar(gimnasioId, pool, causa))
            .scheduler(Scheduler.systemScheduler())
            .build();

    public PoolsPorGimnasio(CatalogoConexiones catalogo, ResolutorSecretos secretos, PropiedadesBd bd) {
        this.catalogo = catalogo;
        this.secretos = secretos;
        this.bd = bd;
    }

    /** El pool del gimnasio. Falla si el gimnasio no tiene una base LISTA (denegar por defecto). */
    public DataSource para(long gimnasioId) {
        return pools.get(gimnasioId, this::crear);
    }

    /** Descarta el pool (ej. después de cambiar la clave del usuario MySQL). Se recrea al próximo uso. */
    public void invalidar(long gimnasioId) {
        pools.invalidate(gimnasioId);
    }

    long cantidadDePools() {
        pools.cleanUp();
        return pools.estimatedSize();
    }

    private HikariDataSource crear(Long gimnasioId) {
        DatosConexion conexion = catalogo.buscar(gimnasioId)
                .filter(c -> "LISTA".equals(c.estado()))
                .orElseThrow(() -> new IllegalStateException("El gimnasio " + gimnasioId + " no tiene una base lista"));

        HikariConfig config = new HikariConfig();
        config.setPoolName("gym-" + gimnasioId);
        config.setJdbcUrl(bd.urlPara(conexion.hostBd(), conexion.puertoBd(), conexion.nombreBd()));
        config.setUsername(conexion.usuarioBd());
        config.setPassword(secretos.resolver(conexion.secretRef()));
        config.setMaximumPoolSize(MAXIMO_CONEXIONES);
        config.setMinimumIdle(0);
        config.setIdleTimeout(Duration.ofMinutes(10).toMillis());
        // Menor que el wait_timeout de MySQL (8 h por defecto): Hikari renueva la conexión antes de que MySQL la corte.
        config.setMaxLifetime(Duration.ofMinutes(30).toMillis());
        config.setConnectionTimeout(Duration.ofSeconds(5).toMillis());
        LOG.info("Creando pool de conexiones del gimnasio {} (base {})", gimnasioId, conexion.nombreBd());
        return new HikariDataSource(config);
    }

    private static void cerrar(Long gimnasioId, HikariDataSource pool, RemovalCause causa) {
        if (pool != null) {
            LOG.info("Cerrando pool del gimnasio {} ({})", gimnasioId, causa);
            pool.close();
        }
    }

    @Override
    public void destroy() {
        pools.invalidateAll();
        pools.cleanUp();
    }
}
