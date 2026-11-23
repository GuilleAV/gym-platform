package com.gimnasio.monitoreo;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Tarea periódica: mide cada carpeta configurada y, si corresponde, avisa.
 *
 * <p>Principio clave: <b>un error nunca corta el monitoreo</b>. Si una carpeta no se puede leer o el correo
 * falla, se registra en el log y se sigue con la siguiente. Un monitor que se cae en silencio es peor que
 * no tener monitor.</p>
 */
public class MonitorEspacioDisco {

    private static final Logger log = LoggerFactory.getLogger(MonitorEspacioDisco.class);

    private final List<Path> rutas;
    private final LectorEspacioDisco lector;
    private final EvaluadorAlertaDisco evaluador;
    private final List<NotificadorAlertas> notificadores;
    private final Clock reloj;

    public MonitorEspacioDisco(List<Path> rutas, LectorEspacioDisco lector, EvaluadorAlertaDisco evaluador,
            List<NotificadorAlertas> notificadores, Clock reloj) {
        this.rutas = List.copyOf(rutas);
        this.lector = Objects.requireNonNull(lector, "lector");
        this.evaluador = Objects.requireNonNull(evaluador, "evaluador");
        this.notificadores = List.copyOf(notificadores);
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Scheduled(fixedDelayString = "${gym.monitoreo.disco.intervalo:PT15M}",
            initialDelayString = "${gym.monitoreo.disco.demora-inicial:PT1M}")
    public void verificar() {
        for (Path ruta : rutas) {
            try {
                EspacioDisco espacio = lector.leer(ruta);
                log.debug("Disco {}: {}% usado", espacio.ruta(), espacio.porcentajeUsado());
                evaluador.evaluar(espacio, reloj.instant()).ifPresent(this::avisar);
            } catch (Exception e) {
                log.error("No se pudo medir el espacio de {}: {}", ruta, e.getMessage());
            }
        }
    }

    private void avisar(AlertaDisco alerta) {
        // El log queda siempre, aunque no haya correo configurado o el correo falle.
        if (alerta.tipo() == AlertaDisco.Tipo.NORMALIZADO) {
            log.info(alerta.asunto());
        } else {
            log.warn(alerta.asunto());
        }
        for (NotificadorAlertas notificador : notificadores) {
            try {
                notificador.notificar(alerta);
            } catch (Exception e) {
                log.error("Falló el aviso por {}: {}", notificador.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
