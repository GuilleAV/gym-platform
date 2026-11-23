package com.gimnasio.monitoreo;

import java.time.Instant;
import java.util.Objects;

/** Aviso que hay que enviar al responsable del servidor. */
public record AlertaDisco(Tipo tipo, EspacioDisco espacio, int umbralPorcentaje, Instant instante) {

    public enum Tipo {
        /** El uso acaba de alcanzar o superar el umbral. */
        SUPERA_UMBRAL,
        /** Sigue por encima del umbral: recordatorio periódico. */
        RECORDATORIO,
        /** Volvió a un nivel normal (por debajo del umbral menos la histéresis). */
        NORMALIZADO
    }

    public AlertaDisco {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(espacio, "espacio");
        Objects.requireNonNull(instante, "instante");
    }

    /** Asunto corto, apto para correo o mensaje. No incluye datos personales. */
    public String asunto() {
        return switch (tipo) {
            case SUPERA_UMBRAL -> "[gym-platform] Disco al %s%% (umbral %d%%)"
                    .formatted(espacio.porcentajeUsado(), umbralPorcentaje);
            case RECORDATORIO -> "[gym-platform] Recordatorio: disco al %s%% (umbral %d%%)"
                    .formatted(espacio.porcentajeUsado(), umbralPorcentaje);
            case NORMALIZADO -> "[gym-platform] Disco normalizado: %s%%"
                    .formatted(espacio.porcentajeUsado());
        };
    }

    /** Texto del aviso con lo necesario para decidir. */
    public String cuerpo() {
        long gb = 1024L * 1024 * 1024;
        return """
                Carpeta monitoreada: %s
                Uso del disco: %s%% (umbral configurado: %d%%)
                Espacio libre: %d GB de %d GB
                Fecha (UTC): %s

                Opciones (ver ADR-0004: docs/adr/0004-almacenamiento-multimedia.md):
                - Liberar espacio o revisar la cuota de los gimnasios que más ocupan.
                - Agrandar el disco/volumen de archivos.
                - Evaluar migrar los archivos a un almacenamiento S3 (adaptador S3Almacenamiento).
                """.formatted(espacio.ruta(), espacio.porcentajeUsado(), umbralPorcentaje,
                espacio.libresBytes() / gb, espacio.totalBytes() / gb, instante);
    }
}
