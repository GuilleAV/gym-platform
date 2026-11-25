/**
 * Módulo <b>notificaciones</b>: envío de correos (y a futuro WhatsApp, push) a usuarios (ADR-0006 §5).
 *
 * <p>API pública: {@link com.gimnasio.notificaciones.Correos} y {@link com.gimnasio.notificaciones.Correo}.
 * El subpaquete {@code smtp} es interno: el resto del sistema no sabe si el correo sale por SMTP, por la API
 * de un proveedor o se guarda en memoria en los tests.</p>
 */
package com.gimnasio.notificaciones;
