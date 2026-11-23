package com.gimnasio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    void dockerComposeUsaLaMismaImagenDeMailpitQueLosTests() throws IOException {
        String imagenMailpit = System.getProperty("mailpit.image");

        assertThat(imagenMailpit).as("la propiedad mailpit.image debe venir del pom.xml").isNotBlank();
        assertThat(Files.readString(DOCKER_COMPOSE))
                .as("infra/docker-compose.yml debe usar la misma imagen de Mailpit que backend/pom.xml")
                .contains("image: " + imagenMailpit);
    }

    @Test
    void todasLasImagenesDelComposeTienenVersionExacta() throws IOException {
        List<String> imagenes = Files.readAllLines(DOCKER_COMPOSE).stream()
                .map(VersionesStackTest::sinComentario)
                .filter(linea -> linea.startsWith("image:"))
                .map(linea -> linea.substring("image:".length()).strip())
                .toList();

        assertThat(imagenes).as("el compose debe declarar al menos una imagen").isNotEmpty();
        // Cada imagen debe terminar en una versión con tres números (ej. 8.4.13 o v1.34.0).
        // Prohibido ":latest", ":edge" o versiones parciales como ":8.4": no son reproducibles.
        assertThat(imagenes)
                .as("imágenes con versión exacta (ADR-0001)")
                .allMatch(imagen -> imagen.matches(".+:v?\\d+\\.\\d+\\.\\d+"));
    }

    @Test
    void todosLosPuertosPublicadosSonSoloDeLocalhost() throws IOException {
        List<String> puertos = Files.readAllLines(DOCKER_COMPOSE).stream()
                .map(VersionesStackTest::sinComentario)
                .filter(linea -> linea.matches("- \"[^\"]*:\\d+\""))
                .toList();

        assertThat(puertos).as("el compose debe publicar al menos un puerto").isNotEmpty();
        // Seguridad: sin "127.0.0.1:" el servicio quedaría accesible desde toda la red.
        assertThat(puertos)
                .as("puertos publicados solo en 127.0.0.1")
                .allMatch(puerto -> puerto.startsWith("- \"127.0.0.1:"));
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

    /** Quita espacios y el comentario YAML del final de la línea ("valor   # comentario" -> "valor"). */
    private static String sinComentario(String linea) {
        return linea.replaceFirst("\\s+#.*$", "").strip();
    }
}
