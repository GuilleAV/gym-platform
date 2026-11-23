package com.gimnasio.monitoreo;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decide CUÁNDO avisar. Es lógica pura: no lee el disco ni manda correos (eso lo hacen los adaptadores),
 * por eso se testea en milisegundos y sin infraestructura.
 *
 * <p>Reglas, para no llenar la bandeja de avisos:</p>
 * <ul>
 *   <li>Avisa una vez al alcanzar o superar el umbral.</li>
 *   <li>Mientras siga arriba, recuerda cada {@code recordatorio} (por defecto 24 h).</li>
 *   <li>Avisa "normalizado" recién al bajar de {@code umbral - histéresis}. Sin ese margen, un disco que
 *       oscila entre 84 % y 86 % mandaría un aviso en cada chequeo.</li>
 * </ul>
 *
 * <p>El estado se guarda en memoria, una entrada por carpeta. Si la app se reinicia estando arriba del umbral,
 * vuelve a avisar: es preferible un aviso de más a uno de menos. Válido mientras haya una sola instancia
 * de la app (ADR-0004); con varias instancias habría que usar ShedLock o guardar el estado en la base.</p>
 */
public class EvaluadorAlertaDisco {

    private final int umbralPorcentaje;
    private final int histeresisPuntos;
    private final Duration recordatorio;

    /** Última vez que se avisó por cada carpeta que está en alerta. Si no está, la carpeta está normal. */
    private final Map<Path, Instant> ultimoAvisoPorRuta = new ConcurrentHashMap<>();

    public EvaluadorAlertaDisco(int umbralPorcentaje, int histeresisPuntos, Duration recordatorio) {
        this.umbralPorcentaje = umbralPorcentaje;
        this.histeresisPuntos = histeresisPuntos;
        this.recordatorio = recordatorio;
    }

    public static EvaluadorAlertaDisco desde(MonitoreoDiscoProperties propiedades) {
        return new EvaluadorAlertaDisco(
                propiedades.umbralPorcentaje(), propiedades.histeresisPuntos(), propiedades.recordatorio());
    }

    public Optional<AlertaDisco> evaluar(EspacioDisco espacio, Instant ahora) {
        double uso = espacio.porcentajeUsado();
        Instant ultimoAviso = ultimoAvisoPorRuta.get(espacio.ruta());
        boolean enAlerta = ultimoAviso != null;

        if (!enAlerta && uso >= umbralPorcentaje) {
            ultimoAvisoPorRuta.put(espacio.ruta(), ahora);
            return Optional.of(new AlertaDisco(AlertaDisco.Tipo.SUPERA_UMBRAL, espacio, umbralPorcentaje, ahora));
        }

        if (enAlerta && uso < umbralPorcentaje - histeresisPuntos) {
            ultimoAvisoPorRuta.remove(espacio.ruta());
            return Optional.of(new AlertaDisco(AlertaDisco.Tipo.NORMALIZADO, espacio, umbralPorcentaje, ahora));
        }

        if (enAlerta && !ahora.isBefore(ultimoAviso.plus(recordatorio))) {
            ultimoAvisoPorRuta.put(espacio.ruta(), ahora);
            return Optional.of(new AlertaDisco(AlertaDisco.Tipo.RECORDATORIO, espacio, umbralPorcentaje, ahora));
        }

        return Optional.empty();
    }
}
