package com.gimnasio.monitoreo;

import java.io.IOException;
import java.nio.file.Path;

/** Puerto: cómo se obtiene el espacio de un disco. Permite usar un lector falso en los tests. */
public interface LectorEspacioDisco {

    EspacioDisco leer(Path ruta) throws IOException;
}
