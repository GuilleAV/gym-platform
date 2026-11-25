package com.gimnasio.notificaciones.smtp;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.gimnasio.notificaciones.Correo;
import com.gimnasio.notificaciones.Correos;

/**
 * Envía los correos por SMTP: Mailpit en desarrollo; en producción el SMTP del proveedor de correo
 * transaccional (spring.mail.* por variables de entorno).
 *
 * <p><b>Por qué en segundo plano:</b> un SMTP puede tardar segundos. Si el request esperara, (1) el usuario
 * esperaría y (2) el tiempo de respuesta delataría si el correo existe ("tardó, entonces le mandó algo").</p>
 *
 * <p><b>Por qué hilos virtuales:</b> enviar un correo es casi todo esperar la red. Un hilo virtual (Java 21+)
 * casi no consume memoria mientras espera, así que pueden salir muchos correos a la vez sin un pool que
 * dimensionar.</p>
 */
@Component
class CorreosSmtp implements Correos, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(CorreosSmtp.class);

    private final JavaMailSender smtp;
    private final String remitente;
    private final ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();

    CorreosSmtp(JavaMailSender smtp, @Value("${gym.correo.remitente}") String remitente) {
        this.smtp = smtp;
        this.remitente = remitente;
    }

    @Override
    public void enviar(Correo correo) {
        hilos.execute(() -> enviarAhora(correo));
    }

    void enviarAhora(Correo correo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(correo.para());
        mensaje.setSubject(correo.asunto());
        mensaje.setText(correo.texto());
        try {
            smtp.send(mensaje);
        } catch (MailException e) {
            // Sin destinatario ni contenido en el log (datos personales, códigos). Solo el asunto y la causa.
            LOG.warn("No se pudo enviar el correo \"{}\": {}", correo.asunto(), e.getClass().getSimpleName());
        }
    }

    /** Al apagar la app, espera a que salgan los correos en curso (como máximo lo que tarde el SMTP). */
    @Override
    public void destroy() {
        hilos.close();
    }
}
