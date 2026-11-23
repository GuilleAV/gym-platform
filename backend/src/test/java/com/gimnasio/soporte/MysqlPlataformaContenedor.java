package com.gimnasio.soporte;

import java.nio.file.Path;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Base para los tests de INTEGRACIÓN que arrancan Spring contra un MySQL real (Testcontainers).
 *
 * <p><b>Qué hace:</b> levanta MySQL 8.4 (la imagen del pom) y le copia el MISMO script de
 * {@code infra/mysql/init/01-plataforma.sh} que usa docker-compose. Así el test verifica el
 * script real: si alguien rompe un GRANT, se entera acá y no en producción.</p>
 *
 * <p><b>Patrón "contenedor único":</b> el contenedor es {@code static} y se arranca una sola vez
 * para TODAS las clases de test que heredan de esta (no una vez por clase). Spring además
 * reutiliza el contexto entre clases con la misma configuración. Resultado: la suite sigue rápida
 * aunque crezca. Testcontainers (Ryuk) apaga el contenedor al terminar la JVM.</p>
 */
public abstract class MysqlPlataformaContenedor {

    /** Claves solo de test: el contenedor vive minutos y es inaccesible desde fuera de la PC/runner. */
    protected static final String CLAVE_APP = "clave-app-de-test";
    protected static final String CLAVE_APROVISIONADOR = "clave-aprovisionador-de-test";

    private static final Path SCRIPT_INIT = Path.of("..", "infra", "mysql", "init", "01-plataforma.sh");

    protected static final MySQLContainer MYSQL =
            new MySQLContainer(DockerImageName.parse(System.getProperty("mysql.image")))
                    .withEnv("GP_APP_DB_PASSWORD", CLAVE_APP)
                    .withEnv("GP_APROVISIONADOR_DB_PASSWORD", CLAVE_APROVISIONADOR)
                    .withCopyFileToContainer(
                            MountableFile.forHostPath(SCRIPT_INIT, 0755),
                            "/docker-entrypoint-initdb.d/01-plataforma.sh");

    static {
        MYSQL.start();
    }

    /** URL JDBC hacia gym_platform dentro del contenedor (el puerto real lo elige Docker). */
    protected static String urlCatalogo() {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/gym_platform?sslMode=PREFERRED&allowPublicKeyRetrieval=true";
    }

    /**
     * Reemplaza en los tests la configuración de application.yml que apunta al MySQL de desarrollo.
     * Mismos usuarios que en producción: la app con gp_app y Flyway con gp_aprovisionador.
     */
    @DynamicPropertySource
    static void propiedadesDeBase(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", MysqlPlataformaContenedor::urlCatalogo);
        registro.add("spring.datasource.password", () -> CLAVE_APP);
        registro.add("spring.flyway.password", () -> CLAVE_APROVISIONADOR);
        registro.add("gym.monitoreo.disco.demora-inicial", () -> "PT1H");
    }
}
