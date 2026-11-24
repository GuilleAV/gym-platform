package com.gimnasio.identidad;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Reglas de contraseñas del sistema.
 *
 * <p><b>Argon2id</b> (ganador de la Password Hashing Competition) es lento y usa mucha memoria a
 * propósito: probar millones de claves con placas de video se vuelve carísimo. Parámetros: la
 * recomendación de OWASP (19 MiB de memoria, 2 iteraciones, 1 hilo). En un servidor chico, cada
 * login usa 19 MiB durante unas decenas de milisegundos: aceptable.</p>
 */
public final class Contrasenias {

    /** Mínimo razonable sin obligar a "símbolos raros" (NIST 800-63B). */
    public static final int LARGO_MINIMO = 12;
    /** Tope: sin él, alguien podría mandar una "clave" de 10 MB y hacer trabajar a Argon2 de más (DoS). */
    public static final int LARGO_MAXIMO = 128;

    private Contrasenias() {
    }

    public static Argon2PasswordEncoder codificador() {
        // saltLength 16 bytes, hashLength 32 bytes, paralelismo 1, memoria 19456 KiB, 2 iteraciones.
        return new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
    }

    public static boolean largoValido(String clave) {
        return clave != null && clave.length() >= LARGO_MINIMO && clave.length() <= LARGO_MAXIMO;
    }
}
