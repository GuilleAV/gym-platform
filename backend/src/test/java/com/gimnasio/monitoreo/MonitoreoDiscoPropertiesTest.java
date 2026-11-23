package com.gimnasio.monitoreo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Una configuración inválida debe fallar al arrancar, no apagar el monitoreo en silencio. */
class MonitoreoDiscoPropertiesTest {

    private static final List<Path> RUTAS = List.of(Path.of("data/media"));
    private static final Duration DIA = Duration.ofHours(24);

    @Test
    void configuracionValida() {
        var propiedades = new MonitoreoDiscoProperties(true, RUTAS, 85, 5, DIA, "  dueno@gimnasio.com ");

        assertThat(propiedades.emailDestino()).isEqualTo("dueno@gimnasio.com");
        assertThat(propiedades.tieneEmailDestino()).isTrue();
    }

    @Test
    void sinEmailSoloQuedaElLog() {
        assertThat(new MonitoreoDiscoProperties(true, RUTAS, 85, 5, DIA, null).tieneEmailDestino()).isFalse();
    }

    @Test
    void rechazaUmbralFueraDeRango() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MonitoreoDiscoProperties(true, RUTAS, 0, 0, DIA, ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MonitoreoDiscoProperties(true, RUTAS, 100, 5, DIA, ""));
    }

    @Test
    void rechazaHisteresisMayorQueElUmbral() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MonitoreoDiscoProperties(true, RUTAS, 10, 10, DIA, ""));
    }

    @Test
    void rechazaHabilitadoSinRutasYRecordatorioInvalido() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MonitoreoDiscoProperties(true, List.of(), 85, 5, DIA, ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MonitoreoDiscoProperties(true, RUTAS, 85, 5, Duration.ZERO, ""));
    }
}
