package com.gimnasio.monitoreo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Usa el disco real de la máquina (carpeta temporal que JUnit borra al final). */
class FileStoreLectorEspacioDiscoTest {

    @TempDir
    Path temporal;

    @Test
    void leeElEspacioRealDelDisco() throws IOException {
        EspacioDisco espacio = new FileStoreLectorEspacioDisco().leer(temporal);

        assertThat(espacio.totalBytes()).isPositive();
        assertThat(espacio.libresBytes()).isBetween(0L, espacio.totalBytes());
        assertThat(espacio.porcentajeUsado()).isBetween(0.0, 100.0);
    }

    @Test
    void creaLaCarpetaSiTodaviaNoExiste() throws IOException {
        Path nueva = temporal.resolve("media").resolve("gym_001");

        new FileStoreLectorEspacioDisco().leer(nueva);

        assertThat(nueva).isDirectory();
    }
}
