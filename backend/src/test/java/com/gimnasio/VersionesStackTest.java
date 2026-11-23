package com.gimnasio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Tests "guardianes" de ADR-0001 (stack y versiones). Son unitarios: no necesitan Docker.
 *
 * <p>¿Por qué testear versiones? Porque una versión escrita solo en un documento no protege nada.
 * Si alguien cambia la imagen de MySQL en un lado y se olvida del otro, desarrollo y tests
 * correrían contra bases distintas: el típico "en mi máquina anda".</p>
 */
class VersionesStackTest {

    /** Viene del pom.xml (propiedad mysql.image) a través de Surefire. */
    private static final String IMAGEN_MYSQL = System.getProperty("mysql.image");

    /** Maven ejecuta los tests con el directorio "backend/" como directorio de trabajo. */
    private static final Path DOCKER_COMPOSE = Path.of("..", "infra", "docker-compose.yml");

    @Test
    void corremosSobreJava25() {
        assertThat(Runtime.version().feature())
                .as("ADR-0001 fija Java 25 LTS")
                .isEqualTo(25);
    }

    @Test
    void laImagenDeMysqlEsUnaVersionExactaDeLaLinea84() {
        assertThat(IMAGEN_MYSQL)
                .as("la propiedad mysql.image debe venir del pom.xml")
                .isNotBlank()
                // mysql:8.4.13 sí; mysql:8.4, mysql:8 o mysql:latest no (no son reproducibles).
                .matches("mysql:8\\.4\\.\\d+");
    }

    @Test
    void dockerComposeUsaLaMismaImagenQueLosTests() throws IOException {
        assertThat(DOCKER_COMPOSE).as("debe existir infra/docker-compose.yml").exists();

        String compose = Files.readString(DOCKER_COMPOSE);

        assertThat(compose)
                .as("infra/docker-compose.yml debe usar la misma imagen que backend/pom.xml")
                .contains("image: " + IMAGEN_MYSQL);
    }

    @Test
    void mysqlDeDesarrolloSoloEscuchaEnLocalhostYNoUsaEl3306() throws IOException {
        String compose = Files.readString(DOCKER_COMPOSE);

        // Seguridad: el puerto publicado tiene que empezar con 127.0.0.1 (solo esta PC).
        assertThat(compose)
                .as("MySQL debe publicarse solo en 127.0.0.1")
                .contains("- \"127.0.0.1:${MYSQL_PORT:-3307}:3306\"");

        // Convivencia: el 3306 de la PC queda libre para otros MySQL (por ejemplo, el del trabajo).
        assertThat(compose)
                .as("no publicar el 3306 de la PC")
                .doesNotContain("\"3306:3306\"")
                .doesNotContain("127.0.0.1:3306:");
    }
}
