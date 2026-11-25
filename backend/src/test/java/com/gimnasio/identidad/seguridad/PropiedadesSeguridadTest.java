package com.gimnasio.identidad.seguridad;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** Unitario: una configuración absurda se rechaza al arrancar, no en producción a las 3 a. m. */
class PropiedadesSeguridadTest {

    @Test
    void laVentanaDebeSerMayorQueElBloqueoMaximo() {
        assertThatThrownBy(() -> new PropiedadesSeguridad.Ingreso(5, 20, Duration.ofMinutes(1), Duration.ofMinutes(15),
                Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ventana");
    }

    @Test
    void losUmbralesDebenSerPositivos() {
        assertThatThrownBy(() -> new PropiedadesSeguridad.Ingreso(0, 20, Duration.ofMinutes(1), Duration.ofMinutes(15),
                Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void faltaElVencimientoAbsoluto() {
        assertThatThrownBy(() -> new PropiedadesSeguridad.Sesion(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void elCodigoPorCorreoNecesitaIntentosYEnviosPositivos() {
        assertThatThrownBy(() -> new PropiedadesSeguridad.CodigoCorreo(Duration.ofMinutes(10), 0, Duration.ofSeconds(60), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PropiedadesSeguridad.CodigoCorreo(null, 5, Duration.ofSeconds(60), 5))
                .isInstanceOf(NullPointerException.class);
    }
}
