package com.gimnasio.plataforma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/** Unitario: el contexto existe solo dentro del bloque, y fuera de él se deniega. */
class ContextoGimnasioTest {

    private static final GimnasioActual NORTE =
            new GimnasioActual(1, "gym-norte", "Gym Norte", ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final GimnasioActual POWER_FIT =
            new GimnasioActual(2, "power-fit", "Power Fit", ZoneId.of("America/Argentina/Cordoba"));

    @Test
    void sinGimnasioSeDeniega() {
        assertThat(ContextoGimnasio.siHay()).isEmpty();
        assertThatThrownBy(ContextoGimnasio::actual)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("denegada");
    }

    @Test
    void dentroDelBloqueHayGimnasioYAlSalirDesaparece() throws Exception {
        String slug = ContextoGimnasio.ejecutarPara(NORTE, () -> ContextoGimnasio.actual().slug());

        assertThat(slug).isEqualTo("gym-norte");
        assertThat(ContextoGimnasio.siHay()).as("al salir del bloque no queda nada").isEmpty();
    }

    @Test
    void siLaOperacionFallaElContextoIgualDesaparece() {
        assertThatThrownBy(() -> ContextoGimnasio.ejecutarPara(NORTE, () -> {
            throw new IllegalArgumentException("falla de negocio");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(ContextoGimnasio.siHay()).isEmpty();
    }

    @Test
    void unBloqueAnidadoNoPisaAlDeAfuera() throws Exception {
        String resultado = ContextoGimnasio.ejecutarPara(NORTE, () -> {
            String adentro = ContextoGimnasio.ejecutarPara(POWER_FIT, () -> ContextoGimnasio.actual().slug());
            return adentro + "|" + ContextoGimnasio.actual().slug();
        });

        assertThat(resultado).isEqualTo("power-fit|gym-norte");
    }
}
