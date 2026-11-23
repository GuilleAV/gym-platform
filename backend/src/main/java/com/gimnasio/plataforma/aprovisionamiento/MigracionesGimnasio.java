package com.gimnasio.plataforma.aprovisionamiento;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

/**
 * Aplica las migraciones del esquema COMÚN de los gimnasios ({@code db/gimnasio}) a una base gym_NNN.
 *
 * <p>Todas las bases de gimnasio tienen el mismo esquema: el mismo V1, V2... Cada base guarda en su
 * propia tabla {@code flyway_schema_history} hasta dónde llegó, así que se pueden actualizar de a una
 * y ver cuál falló.</p>
 */
final class MigracionesGimnasio {

    static final String UBICACION = "classpath:db/gimnasio";

    private MigracionesGimnasio() {
    }

    /** Migra la base y devuelve la versión del esquema en la que quedó. */
    static String migrar(String url, String usuario, String password) {
        Flyway flyway = Flyway.configure()
                .dataSource(url, usuario, password)
                .locations(UBICACION)
                .validateOnMigrate(true)
                .load();
        flyway.migrate();
        MigrationInfo actual = flyway.info().current();
        return actual == null ? null : actual.getVersion().getVersion();
    }
}
