package com.gimnasio.identidad.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unitario: el paso "pasó la contraseña, falta el código" vence a los 5 minutos. */
class IngresoPendienteTest {

    private static final long DESDE = 1_000_000L;
    private static final long CINCO_MINUTOS = 5 * 60 * 1000L;

    @Test
    void esVigenteDuranteCincoMinutos() {
        assertThat(IngresoPendiente.vigente(DESDE, DESDE)).isTrue();
        assertThat(IngresoPendiente.vigente(DESDE, DESDE + CINCO_MINUTOS - 1)).isTrue();
    }

    @Test
    void venceAlCumplirseLosCincoMinutos() {
        assertThat(IngresoPendiente.vigente(DESDE, DESDE + CINCO_MINUTOS)).isFalse();
    }

    @Test
    void unaFechaEnElFuturoNoEsVigente() {
        // Si el reloj "vuelve atrás" o el dato está alterado, ante la duda se deniega.
        assertThat(IngresoPendiente.vigente(DESDE, DESDE - 1)).isFalse();
    }
}
