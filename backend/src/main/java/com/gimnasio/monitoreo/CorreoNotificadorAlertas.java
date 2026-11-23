package com.gimnasio.monitoreo;

import java.util.Objects;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Adaptador de correo. En desarrollo el SMTP es Mailpit (http://localhost:8025). */
public class CorreoNotificadorAlertas implements NotificadorAlertas {

    static final String REMITENTE = "alertas@gym-platform.local";

    private final JavaMailSender enviadorCorreo;
    private final String destinatario;

    public CorreoNotificadorAlertas(JavaMailSender enviadorCorreo, String destinatario) {
        this.enviadorCorreo = Objects.requireNonNull(enviadorCorreo, "enviadorCorreo");
        if (destinatario == null || destinatario.isBlank()) {
            throw new IllegalArgumentException("destinatario es obligatorio");
        }
        this.destinatario = destinatario;
    }

    @Override
    public void notificar(AlertaDisco alerta) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(REMITENTE);
        mensaje.setTo(destinatario);
        mensaje.setSubject(alerta.asunto());
        mensaje.setText(alerta.cuerpo());
        enviadorCorreo.send(mensaje);
    }
}
