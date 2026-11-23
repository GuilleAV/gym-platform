package com.gimnasio.monitoreo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** El monitor nunca debe cortarse por un error: ni de lectura de una carpeta ni del aviso. */
class MonitorEspacioDiscoTest {

    private static final Path MEDIA = Path.of("/srv/media");
    private static final Path ROTA = Path.of("/no/se/puede/leer");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);

    /** Lector falso: devuelve el porcentaje configurado, o falla para la ruta "rota". */
    private record LectorFalso(Map<Path, Integer> porcentajes) implements LectorEspacioDisco {
        @Override
        public EspacioDisco leer(Path ruta) throws IOException {
            if (ruta.equals(ROTA)) {
                throw new IOException("disco desconectado");
            }
            return new EspacioDisco(ruta, 100, 100 - porcentajes.get(ruta));
        }
    }

    private static final class NotificadorQueAnota implements NotificadorAlertas {
        final List<AlertaDisco> recibidas = new ArrayList<>();

        @Override
        public void notificar(AlertaDisco alerta) {
            recibidas.add(alerta);
        }
    }

    private static MonitorEspacioDisco monitor(List<Path> rutas, int porcentajeMedia, List<NotificadorAlertas> notificadores) {
        return new MonitorEspacioDisco(rutas, new LectorFalso(Map.of(MEDIA, porcentajeMedia)),
                new EvaluadorAlertaDisco(85, 5, Duration.ofHours(24)), notificadores, RELOJ);
    }

    @Test
    void avisaCuandoSuperaElUmbral() {
        var notificador = new NotificadorQueAnota();

        monitor(List.of(MEDIA), 90, List.of(notificador)).verificar();

        assertThat(notificador.recibidas).singleElement()
                .extracting(AlertaDisco::tipo).isEqualTo(AlertaDisco.Tipo.SUPERA_UMBRAL);
    }

    @Test
    void noAvisaSiEstaDebajoDelUmbral() {
        var notificador = new NotificadorQueAnota();

        monitor(List.of(MEDIA), 50, List.of(notificador)).verificar();

        assertThat(notificador.recibidas).isEmpty();
    }

    @Test
    void unaCarpetaIlegibleNoImpideRevisarLasDemas() {
        var notificador = new NotificadorQueAnota();

        monitor(List.of(ROTA, MEDIA), 90, List.of(notificador)).verificar();

        assertThat(notificador.recibidas).hasSize(1);
    }

    @Test
    void siUnNotificadorFallaLosDemasIgualReciben() {
        NotificadorAlertas roto = alerta -> {
            throw new IllegalStateException("SMTP caído");
        };
        var notificador = new NotificadorQueAnota();

        monitor(List.of(MEDIA), 90, List.of(roto, notificador)).verificar();

        assertThat(notificador.recibidas).hasSize(1);
    }
}
