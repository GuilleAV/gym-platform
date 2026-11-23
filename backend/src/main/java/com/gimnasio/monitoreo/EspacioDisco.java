package com.gimnasio.monitoreo;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Foto del espacio de un disco en un momento dado. Es un "value object": inmutable y sin lógica externa.
 *
 * @param ruta        carpeta monitoreada (por ejemplo, la raíz de los archivos multimedia)
 * @param totalBytes  capacidad total del disco donde vive esa carpeta
 * @param libresBytes bytes que la aplicación todavía puede usar
 */
public record EspacioDisco(Path ruta, long totalBytes, long libresBytes) {

    public EspacioDisco {
        Objects.requireNonNull(ruta, "ruta");
        if (totalBytes <= 0) {
            throw new IllegalArgumentException("totalBytes debe ser mayor a 0");
        }
        if (libresBytes < 0 || libresBytes > totalBytes) {
            throw new IllegalArgumentException("libresBytes debe estar entre 0 y totalBytes");
        }
    }

    /** Porcentaje usado, de 0 a 100, con un decimal (87.3). */
    public double porcentajeUsado() {
        double usado = (double) (totalBytes - libresBytes) / totalBytes * 100.0;
        return Math.round(usado * 10.0) / 10.0;
    }
}
