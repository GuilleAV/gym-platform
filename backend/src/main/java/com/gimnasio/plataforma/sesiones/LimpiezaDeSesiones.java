package com.gimnasio.plataforma.sesiones;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gimnasio.plataforma.conexiones.PoolsPorGimnasio;

/**
 * Borra las sesiones vencidas de CADA gimnasio (ADR-0002 §1).
 *
 * <p>Spring Session trae su propia limpieza, pero corre sin gimnasio en el contexto: no sabría en
 * qué base borrar. Por eso se desactiva ({@code spring.session.jdbc.cleanup-cron: "-"}) y este job
 * recorre los gimnasios con base LISTA. Si falla en uno, lo registra y sigue con el resto: un
 * gimnasio con problemas no deja a los demás sin limpieza.</p>
 */
@Component
public class LimpiezaDeSesiones {

    private static final Logger LOG = LoggerFactory.getLogger(LimpiezaDeSesiones.class);

    private final JdbcTemplate catalogo;
    private final PoolsPorGimnasio pools;

    LimpiezaDeSesiones(JdbcTemplate catalogo, PoolsPorGimnasio pools) {
        this.catalogo = catalogo;
        this.pools = pools;
    }

    /** Devuelve cuántas sesiones borró en total. */
    @Scheduled(fixedDelayString = "${gym.sesiones.limpieza:PT10M}", initialDelayString = "${gym.sesiones.limpieza:PT10M}")
    public int limpiar() {
        List<Long> gimnasios = catalogo.queryForList(
                "SELECT gimnasio_id FROM gimnasio_conexion WHERE estado = 'LISTA'", Long.class);
        int total = 0;
        long ahora = System.currentTimeMillis();
        for (Long gimnasioId : gimnasios) {
            try {
                total += new JdbcTemplate(pools.para(gimnasioId)).update("DELETE FROM sesion WHERE EXPIRY_TIME < ?", ahora);
            } catch (RuntimeException e) {
                LOG.warn("No se pudieron limpiar las sesiones del gimnasio {}: {}", gimnasioId, e.getClass().getSimpleName());
            }
        }
        return total;
    }
}
