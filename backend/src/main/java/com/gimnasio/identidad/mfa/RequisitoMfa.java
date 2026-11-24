package com.gimnasio.identidad.mfa;

/** Qué le falta a un usuario, después de la contraseña, para terminar de ingresar (ADR-0005 §2). */
public enum RequisitoMfa {
    /** Ninguno de sus roles exige MFA: con la contraseña alcanza. */
    NO_REQUERIDO,
    /** Algún rol exige MFA y ya tiene la app configurada: tiene que ingresar un código. */
    CODIGO,
    /** Algún rol exige MFA y todavía no configuró la app: tiene que escanear el QR primero. */
    CONFIGURAR
}
