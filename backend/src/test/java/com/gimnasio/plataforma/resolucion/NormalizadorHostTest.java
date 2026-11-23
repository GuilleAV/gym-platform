package com.gimnasio.plataforma.resolucion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Unitario: el mismo dominio escrito de distintas formas debe dar UNA sola identidad. */
class NormalizadorHostTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "gym-norte.localhost,    gym-norte.localhost",
            "Gym-Norte.LOCALHOST,    gym-norte.localhost",   // mayúsculas
            "gym-norte.localhost.,   gym-norte.localhost",   // punto final (válido en DNS)
            "'  gym-norte.com  ',    gym-norte.com",         // espacios alrededor
            "gimnasio-ñandú.com,     xn--gimnasio-and-ihb3k.com", // acentos -> punycode
    })
    void normalizaVariantesDelMismoHost(String entrada, String esperado) {
        assertThat(NormalizadorHost.normalizar(entrada)).contains(esperado);
    }

    @ParameterizedTest(name = "rechaza [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "gym norte.com",       // espacio
            "gym_norte.com",       // guion bajo: no válido en nombres de host
            "gym-norte.com:8080",  // con puerto
            "gym-norte.com/admin", // con ruta
            "gym..norte.com",      // etiqueta vacía
            "' OR 1=1 --",         // basura / intento de inyección
    })
    void rechazaLoQueNoEsUnHostValido(String entrada) {
        assertThat(NormalizadorHost.normalizar(entrada)).isEmpty();
    }

    @Test
    void rechazaHostsDemasiadoLargos() {
        String largo = ("a".repeat(60) + ".").repeat(5) + "com"; // > 253 caracteres
        assertThat(NormalizadorHost.normalizar(largo)).isEmpty();
    }
}
