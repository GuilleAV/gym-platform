package com.gimnasio.notificaciones.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.gimnasio.notificaciones.Correo;

/**
 * Integración (Docker): el correo sale de verdad por SMTP a un Mailpit y se lee con su API HTTP.
 * Prueba también que el envío es asíncrono y que un SMTP caído no rompe a quien llama.
 */
@Testcontainers
class CorreosSmtpIT {

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
    void elCorreoLlegaAlBuzonConAcentosYDesdeElRemitenteConfigurado() throws Exception {
        CorreosSmtp correos = new CorreosSmtp(smtp(MAILPIT.getMappedPort(SMTP)), "no-responder@gimnasio.test");

        correos.enviar(new Correo("ana@gimnasio.test", "123456 es tu código para ingresar a Gym Ñandú", "Código: 123456"));

        String buzon = esperarBuzonCon("ana@gimnasio.test");
        assertThat(buzon).contains("no-responder@gimnasio.test").contains("Gym Ñandú");
        correos.destroy();
    }

    @Test
    void conElSmtpCaidoQuienEnviaNoSeEnteraNiFalla() {
        CorreosSmtp correos = new CorreosSmtp(smtp(1), "no-responder@gimnasio.test"); // puerto 1: nadie escucha

        correos.enviarAhora(new Correo("nadie@gimnasio.test", "Asunto", "Texto")); // no lanza excepción
        correos.destroy();
    }

    @Test
    void elToStringNoMuestraDestinatarioNiContenido() {
        String texto = new Correo("ana@gimnasio.test", "Asunto", "código 654321").toString();
        assertThat(texto).doesNotContain("ana@").doesNotContain("654321");
    }

    private static JavaMailSenderImpl smtp(int puerto) {
        JavaMailSenderImpl enviador = new JavaMailSenderImpl();
        enviador.setHost(MAILPIT.getHost());
        enviador.setPort(puerto);
        enviador.setDefaultEncoding("UTF-8");
        enviador.getJavaMailProperties().put("mail.smtp.connectiontimeout", "2000");
        return enviador;
    }

    /** El envío es asíncrono: se consulta el buzón hasta 10 segundos. */
    private static String esperarBuzonCon(String texto) throws IOException, InterruptedException {
        HttpRequest consulta = HttpRequest.newBuilder(URI.create("http://%s:%d/api/v1/messages"
                .formatted(MAILPIT.getHost(), MAILPIT.getMappedPort(WEB)))).build();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            for (int i = 0; i < 50; i++) {
                String buzon = cliente.send(consulta, HttpResponse.BodyHandlers.ofString()).body();
                if (buzon.contains(texto)) {
                    return buzon;
                }
                Thread.sleep(200);
            }
        }
        throw new AssertionError("El correo no llegó a Mailpit en 10 segundos");
    }
}
