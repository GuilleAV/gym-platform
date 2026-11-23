package com.gimnasio.monitoreo;

/**
 * Puerto: cómo se le avisa a una persona. Hoy por correo; mañana puede sumarse WhatsApp
 * sin tocar la lógica que decide cuándo avisar.
 */
public interface NotificadorAlertas {

    void notificar(AlertaDisco alerta);
}
