package com.gimnasio.identidad.mfa;

/**
 * Lo que necesita la pantalla para configurar la app: el secreto (para cargarlo a mano si no puede
 * escanear) y la URI {@code otpauth://} que se dibuja como código QR.
 */
public record ConfiguracionMfa(String secreto, String uri) {

    /** Evita que el secreto termine en un log si alguien imprime el objeto. */
    @Override
    public String toString() {
        return "ConfiguracionMfa[secreto=***, uri=***]";
    }
}
