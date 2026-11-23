package com.gimnasio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del backend.
 *
 * <p>Está en el paquete raíz {@code com.gimnasio} a propósito: Spring escanea este paquete
 * y todos sus subpaquetes. Cada módulo de la arquitectura (plataforma, identidad, alumnos,
 * acceso, comercial, pagos...) va a vivir en su propio subpaquete, por ejemplo
 * {@code com.gimnasio.alumnos}. Más adelante, Spring Modulith va a verificar en los tests
 * que un módulo no use las clases internas de otro.</p>
 */
@SpringBootApplication
public class GimnasioApplication {

    public static void main(String[] args) {
        SpringApplication.run(GimnasioApplication.class, args);
    }
}
