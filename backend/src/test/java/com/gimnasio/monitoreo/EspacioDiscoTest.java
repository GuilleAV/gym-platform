package com.gimnasio.monitoreo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class EspacioDiscoTest {

    private static final Path RUTA = Path.of("/srv/media");

    @Test
    void calculaElPorcentajeUsadoConUnDecimal() {
        // 1000 total, 127 libres -> 87.3 % usado
        assertThat(new EspacioDisco(RUTA, 1000, 127).porcentajeUsado()).isEqualTo(87.3);
    }

    @Test
    void discoVacioYDiscoLleno() {
        assertThat(new EspacioDisco(RUTA, 1000, 1000).porcentajeUsado()).isEqualTo(0.0);
        assertThat(new EspacioDisco(RUTA, 1000, 0).porcentajeUsado()).isEqualTo(100.0);
    }

    @Test
    void rechazaValoresImposibles() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EspacioDisco(RUTA, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new EspacioDisco(RUTA, 100, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> new EspacioDisco(RUTA, 100, 101));
    }

    @Test
    void elAvisoIncluyeLoNecesarioParaDecidir() {
        long gb = 1024L * 1024 * 1024;
        EspacioDisco espacio = new EspacioDisco(RUTA, 100 * gb, 13 * gb);
        AlertaDisco alerta = new AlertaDisco(AlertaDisco.Tipo.SUPERA_UMBRAL, espacio, 85,
                Instant.parse("2026-11-23T12:00:00Z"));

        assertThat(alerta.asunto()).contains("87.0%").contains("85%");
        assertThat(alerta.cuerpo()).contains("13 GB de 100 GB").contains("ADR-0004");
    }
}
