package com.gimnasio.identidad;

/**
 * Un rol del gimnasio (ADR-0005).
 *
 * @param codigo identificador fijo que usa el código (ej. {@code RECEPCION})
 * @param nombre nombre visible y editable (ej. "Recepción / caja")
 * @param mfa    si exige segundo factor al ingresar
 * @param activo un rol desactivado no cuenta para nada (ni para MFA ni, a futuro, para permisos)
 */
public record Rol(String codigo, String nombre, boolean mfa, boolean activo) {
}
