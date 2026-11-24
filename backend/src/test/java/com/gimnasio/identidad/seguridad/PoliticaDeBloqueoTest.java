package com.gimnasio.identidad.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unitario: el bloqueo es progresivo (se duplica) y tiene tope. */
class PoliticaDeBloqueoTest {

    private final PoliticaDeBloqueo politica = new PoliticaDeBloqueo(5, Duration.ofMinutes(1), Duration.ofMinutes(15));

    @ParameterizedTest(name = "{0} fallos → {1} min")
    @CsvSource({
            "0, 0", "1, 0", "4, 0",   // por debajo del umbral: nada
            "5, 1",                   // primer bloqueo
            "6, 2", "7, 4", "8, 8",   // se duplica
            "9, 15", "10, 15"         // tope
    })
    void bloqueoSegunFallos(int fallos, long minutos) {
        assertThat(politica.bloqueoPara(fallos)).isEqualTo(Duration.ofMinutes(minutos));
    }

    @Test
    void muchisimosFallosNoDesbordanYQuedanEnElTope() {
        assertThat(politica.bloqueoPara(Integer.MAX_VALUE)).isEqualTo(Duration.ofMinutes(15));
        assertThat(politica.bloqueoPara(1_000)).isEqualTo(Duration.ofMinutes(15));
    }
}
