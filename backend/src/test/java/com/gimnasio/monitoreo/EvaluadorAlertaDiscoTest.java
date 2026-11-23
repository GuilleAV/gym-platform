package com.gimnasio.monitoreo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Reglas de cuándo avisar: umbral, recordatorio cada 24 h e histéresis. */
class EvaluadorAlertaDiscoTest {

    private static final Path MEDIA = Path.of("/srv/media");
    private static final Path BASE = Path.of("/srv/mysql");
    private static final Instant T0 = Instant.parse("2026-11-23T12:00:00Z");

    private final EvaluadorAlertaDisco evaluador = new EvaluadorAlertaDisco(85, 5, Duration.ofHours(24));

    /** Disco de 100 bytes: el porcentaje usado es directo. */
    private static EspacioDisco usado(Path ruta, int porcentaje) {
        return new EspacioDisco(ruta, 100, 100 - porcentaje);
    }

    @Test
    void debajoDelUmbralNoAvisa() {
        assertThat(evaluador.evaluar(usado(MEDIA, 84), T0)).isEmpty();
    }

    @Test
    void alcanzarExactamenteElUmbralAvisa() {
        Optional<AlertaDisco> alerta = evaluador.evaluar(usado(MEDIA, 85), T0);

        assertThat(alerta).get().extracting(AlertaDisco::tipo).isEqualTo(AlertaDisco.Tipo.SUPERA_UMBRAL);
    }

    @Test
    void siSigueArribaNoRepiteAntesDe24Horas() {
        evaluador.evaluar(usado(MEDIA, 90), T0);

        assertThat(evaluador.evaluar(usado(MEDIA, 91), T0.plus(Duration.ofHours(23)))).isEmpty();
    }

    @Test
    void siSigueArribaRecuerdaALas24Horas() {
        evaluador.evaluar(usado(MEDIA, 90), T0);

        Optional<AlertaDisco> alerta = evaluador.evaluar(usado(MEDIA, 91), T0.plus(Duration.ofHours(24)));

        assertThat(alerta).get().extracting(AlertaDisco::tipo).isEqualTo(AlertaDisco.Tipo.RECORDATORIO);
    }

    @Test
    void bajarApenasDelUmbralNoCuentaComoNormalizado() {
        evaluador.evaluar(usado(MEDIA, 86), T0);

        // 84 % y 80 % siguen dentro del margen de histéresis (umbral 85 - 5 = 80).
        assertThat(evaluador.evaluar(usado(MEDIA, 84), T0.plusSeconds(900))).isEmpty();
        assertThat(evaluador.evaluar(usado(MEDIA, 80), T0.plusSeconds(1800))).isEmpty();
        // Y si vuelve a 86 % no manda otro "supera umbral": seguía en alerta.
        assertThat(evaluador.evaluar(usado(MEDIA, 86), T0.plusSeconds(2700))).isEmpty();
    }

    @Test
    void bajarDelUmbralMenosLaHisteresisAvisaNormalizadoYPermiteVolverAAvisar() {
        evaluador.evaluar(usado(MEDIA, 90), T0);

        Optional<AlertaDisco> normalizado = evaluador.evaluar(usado(MEDIA, 79), T0.plusSeconds(900));
        Optional<AlertaDisco> otraVez = evaluador.evaluar(usado(MEDIA, 88), T0.plusSeconds(1800));

        assertThat(normalizado).get().extracting(AlertaDisco::tipo).isEqualTo(AlertaDisco.Tipo.NORMALIZADO);
        assertThat(otraVez).get().extracting(AlertaDisco::tipo).isEqualTo(AlertaDisco.Tipo.SUPERA_UMBRAL);
    }

    @Test
    void cadaCarpetaTieneSuPropioEstado() {
        evaluador.evaluar(usado(MEDIA, 90), T0);

        Optional<AlertaDisco> alertaBase = evaluador.evaluar(usado(BASE, 90), T0.plusSeconds(60));

        assertThat(alertaBase).get().extracting(AlertaDisco::tipo).isEqualTo(AlertaDisco.Tipo.SUPERA_UMBRAL);
    }
}
