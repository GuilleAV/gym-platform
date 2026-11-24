package com.gimnasio.identidad.seguridad;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros de seguridad del inicio de sesión ({@code gym.seguridad.*} en application.yml).
 *
 * <p>Están en configuración y no fijos en el código para poder ajustarlos sin recompilar
 * (por ejemplo, si un gimnasio grande comparte una sola IP pública y el límite por IP le queda corto).</p>
 */
@ConfigurationProperties("gym.seguridad")
public record PropiedadesSeguridad(Ingreso ingreso, Sesion sesion) {

    public PropiedadesSeguridad {
        Objects.requireNonNull(ingreso, "falta gym.seguridad.ingreso");
        Objects.requireNonNull(sesion, "falta gym.seguridad.sesion");
    }

    /**
     * @param fallosPorCuenta fallos seguidos de un mismo correo antes del primer bloqueo
     * @param fallosPorIp     fallos desde una misma IP (con cualquier correo) antes del primer bloqueo
     * @param bloqueoInicial  duración del primer bloqueo; cada fallo posterior la duplica
     * @param bloqueoMaximo   tope del bloqueo
     * @param ventana         si pasa este tiempo sin fallos, el contador vuelve a cero
     */
    public record Ingreso(int fallosPorCuenta, int fallosPorIp, Duration bloqueoInicial, Duration bloqueoMaximo,
            Duration ventana) {

        public Ingreso {
            if (fallosPorCuenta < 1 || fallosPorIp < 1) {
                throw new IllegalArgumentException("los umbrales de fallos deben ser 1 o más");
            }
            Objects.requireNonNull(bloqueoInicial, "falta bloqueo-inicial");
            Objects.requireNonNull(bloqueoMaximo, "falta bloqueo-maximo");
            Objects.requireNonNull(ventana, "falta ventana");
            if (ventana.compareTo(bloqueoMaximo) <= 0) {
                // Si la ventana fuera más corta que el bloqueo, el contador se reiniciaría durante el
                // bloqueo y el castigo nunca crecería.
                throw new IllegalArgumentException("la ventana debe ser mayor que el bloqueo máximo");
            }
        }
    }

    /** @param vencimientoAbsoluto vida máxima de una sesión desde el ingreso, aunque se use sin parar */
    public record Sesion(Duration vencimientoAbsoluto) {

        public Sesion {
            Objects.requireNonNull(vencimientoAbsoluto, "falta vencimiento-absoluto");
        }
    }
}
