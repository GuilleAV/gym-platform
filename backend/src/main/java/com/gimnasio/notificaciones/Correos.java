package com.gimnasio.notificaciones;

/**
 * Envía correos. El envío es <b>asíncrono</b>: el método vuelve enseguida y el correo sale en segundo
 * plano. Si falla, queda en el log (sin destinatario ni contenido) y no se reintenta (ADR-0006 §5).
 */
public interface Correos {

    void enviar(Correo correo);
}
