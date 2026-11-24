package com.gimnasio.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unitario: Argon2id con los parámetros de OWASP y reglas de largo. */
class ContraseniasTest {

    private final PasswordEncoder codificador = Contrasenias.codificador();

    @Test
    void usaArgon2idConLosParametrosDeOwasp() {
        String hash = codificador.encode("una-clave-larga-de-prueba");
        // Formato estándar PHC: algoritmo, versión, memoria (KiB), iteraciones, paralelismo, sal, hash.
        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
    }

    @Test
    void verificaLaClaveCorrectaYRechazaLaIncorrecta() {
        String hash = codificador.encode("una-clave-larga-de-prueba");
        assertThat(codificador.matches("una-clave-larga-de-prueba", hash)).isTrue();
        assertThat(codificador.matches("una-clave-larga-de-pruebA", hash)).isFalse();
    }

    @Test
    void laMismaClaveDaHashesDistintos() {
        // Sal aleatoria: dos usuarios con la misma clave no tienen el mismo hash.
        assertThat(codificador.encode("misma-clave-123")).isNotEqualTo(codificador.encode("misma-clave-123"));
    }

    @Test
    void reglasDeLargo() {
        assertThat(Contrasenias.largoValido("corta")).isFalse();
        assertThat(Contrasenias.largoValido("x".repeat(12))).isTrue();
        assertThat(Contrasenias.largoValido("x".repeat(128))).isTrue();
        assertThat(Contrasenias.largoValido("x".repeat(129))).as("tope contra DoS").isFalse();
        assertThat(Contrasenias.largoValido(null)).isFalse();
    }
}
