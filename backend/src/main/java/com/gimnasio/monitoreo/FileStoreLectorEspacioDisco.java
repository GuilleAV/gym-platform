package com.gimnasio.monitoreo;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adaptador real: usa la API estándar de Java (NIO {@link FileStore}), que funciona igual en Linux y Windows.
 *
 * <p>Si la carpeta todavía no existe (instalación nueva) la crea, para medir el disco donde va a vivir.</p>
 */
public class FileStoreLectorEspacioDisco implements LectorEspacioDisco {

    @Override
    public EspacioDisco leer(Path ruta) throws IOException {
        Path absoluta = ruta.toAbsolutePath().normalize();
        Files.createDirectories(absoluta);
        FileStore disco = Files.getFileStore(absoluta);
        // getUsableSpace (no getUnallocatedSpace): descuenta el espacio reservado que la app no puede usar.
        return new EspacioDisco(absoluta, disco.getTotalSpace(), disco.getUsableSpace());
    }
}
