package com.gimnasio.monitoreo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test de INTEGRACIÓN (requiere Docker): manda un aviso real por SMTP a un Mailpit levantado con
 * Testcontainers y lo verifica con la API HTTP de Mailpit. Prueba el camino completo del correo.
 */
@Testcontainers
class CorreoNotificadorAlertasIT {

    private static final int SMTP = 1025;
    private static final int WEB = 8025;

    @Container
    static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(DockerImageName.parse(System.getProperty("mailpit.image")))
                    .withExposedPorts(SMTP, WEB)
                    .withEnv("MP_SMTP_AUTH_ACCEPT_ANY", "1")
                    .withEnv("MP_SMTP_AUTH_ALLOW_INSECURE", "1")
                    .waitingFor(Wait.forHttp("/readyz").forPort(WEB));

    @Test
    void elAvisoLlegaAlBuzon() throws IOException, InterruptedException {
        JavaMailSenderImpl enviador = new JavaMailSenderImpl();
        enviador.setHost(MAILPIT.getHost());
        enviador.setPort(MAILPIT.getMappedPort(SMTP));
        enviador.setDefaultEncoding("UTF-8");

        long gb = 1024L * 1024 * 1024;
        AlertaDisco alerta = new AlertaDisco(AlertaDisco.Tipo.SUPERA_UMBRAL,
                new EspacioDisco(Path.of("/srv/media"), 100 * gb, 10 * gb), 85, Instant.now());

        new CorreoNotificadorAlertas(enviador, "dueno@gimnasio.test").notificar(alerta);

        String buzon = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://%s:%d/api/v1/messages"
                        .formatted(MAILPIT.getHost(), MAILPIT.getMappedPort(WEB)))).build(),
                HttpResponse.BodyHandlers.ofString()).body();

        assertThat(buzon)
                .contains("dueno@gimnasio.test")
                .contains("Disco al 90.0%")
                .contains(CorreoNotificadorAlertas.REMITENTE);
    }
}
