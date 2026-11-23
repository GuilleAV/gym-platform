package com.gimnasio.monitoreo;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del monitoreo de disco (prefijo {@code gym.monitoreo.disco} en application.yml).
 *
 * <p>Se valida al arrancar: una configuración inválida hace fallar el arranque en vez de dejar
 * el monitoreo apagado sin que nadie se entere.</p>
 *
 * @param habilitado        si el chequeo periódico corre
 * @param rutas             carpetas a vigilar (archivos multimedia y, si está en otro disco, la base)
 * @param umbralPorcentaje  porcentaje de uso que dispara el aviso (por defecto 85)
 * @param histeresisPuntos  puntos por debajo del umbral para considerar "normalizado" (por defecto 5)
 * @param recordatorio      cada cuánto repetir el aviso mientras siga arriba (por defecto 24 h)
 * @param emailDestino      a quién avisar; vacío = solo se registra en el log
 */
@ConfigurationProperties(prefix = "gym.monitoreo.disco")
public record MonitoreoDiscoProperties(
        boolean habilitado,
        List<Path> rutas,
        int umbralPorcentaje,
        int histeresisPuntos,
        Duration recordatorio,
        String emailDestino) {

    public MonitoreoDiscoProperties {
        rutas = rutas == null ? List.of() : List.copyOf(rutas);
        if (habilitado && rutas.isEmpty()) {
            throw new IllegalArgumentException("gym.monitoreo.disco.rutas no puede estar vacío si el monitoreo está habilitado");
        }
        if (umbralPorcentaje < 1 || umbralPorcentaje > 99) {
            throw new IllegalArgumentException("gym.monitoreo.disco.umbral-porcentaje debe estar entre 1 y 99");
        }
        if (histeresisPuntos < 0 || histeresisPuntos >= umbralPorcentaje) {
            throw new IllegalArgumentException("gym.monitoreo.disco.histeresis-puntos debe estar entre 0 y el umbral");
        }
        if (recordatorio == null || recordatorio.isNegative() || recordatorio.isZero()) {
            throw new IllegalArgumentException("gym.monitoreo.disco.recordatorio debe ser una duración positiva");
        }
        emailDestino = emailDestino == null ? "" : emailDestino.strip();
    }

    public boolean tieneEmailDestino() {
        return !emailDestino.isEmpty();
    }
}
