package com.gimnasio.identidad.seguridad;

import java.time.Duration;

/**
 * Cuánto se bloquea según la cantidad de fallos seguidos: <b>bloqueo progresivo</b>.
 *
 * <p>Ejemplo con umbral 5, inicial 1 min y máximo 15 min: del 1.º al 4.º fallo, nada; al 5.º, 1 min;
 * al 6.º, 2 min; al 7.º, 4 min; al 8.º, 8 min; del 9.º en adelante, 15 min.</p>
 *
 * <p><b>Por qué progresivo y con tope:</b> a quien se equivocó un par de veces casi no lo afecta, pero
 * un robot que prueba claves queda limitado a unos 4 intentos por hora. El tope evita el otro riesgo:
 * que un atacante deje a un usuario real afuera por días con solo fallar a propósito con su correo.</p>
 *
 * <p>Es una clase pura (sin base ni reloj): se prueba con tests unitarios simples.</p>
 */
record PoliticaDeBloqueo(int umbral, Duration bloqueoInicial, Duration bloqueoMaximo) {

    /** Más allá de este exponente el bloqueo ya pasó cualquier tope razonable; evita desbordes. */
    private static final int EXPONENTE_MAXIMO = 20;

    Duration bloqueoPara(int fallos) {
        if (fallos < umbral) {
            return Duration.ZERO;
        }
        int exponente = Math.min(fallos - umbral, EXPONENTE_MAXIMO);
        Duration bloqueo = bloqueoInicial.multipliedBy(1L << exponente);
        return bloqueo.compareTo(bloqueoMaximo) > 0 ? bloqueoMaximo : bloqueo;
    }
}
