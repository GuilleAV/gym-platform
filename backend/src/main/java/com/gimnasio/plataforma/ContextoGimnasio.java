package com.gimnasio.plataforma;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * "¿De qué gimnasio es este request?" — el contexto de tenant.
 *
 * <p><b>Por qué {@link ScopedValue} y no {@code ThreadLocal}:</b> con un ThreadLocal hay que
 * acordarse de limpiarlo en un {@code finally}; si alguien se olvida, el hilo vuelve al pool de
 * Tomcat con el gimnasio anterior y el próximo request (¡de otro gimnasio!) lo hereda. Con
 * ScopedValue (Java 25) el valor solo existe DENTRO del bloque {@code where(...).call(...)} y
 * desaparece al salir, incluso si hay una excepción. La limpieza la garantiza el lenguaje.</p>
 *
 * <p>Regla del proyecto: el gimnasio sale del dominio + sesión, nunca de un dato que mande el
 * cliente. Por eso este contexto solo lo fija el filtro de resolución (y, en el futuro, los jobs
 * que recorren gimnasios).</p>
 */
public final class ContextoGimnasio {

    private static final ScopedValue<GimnasioActual> ACTUAL = ScopedValue.newInstance();

    private ContextoGimnasio() {
    }

    /**
     * El gimnasio actual. Si no hay ninguno, FALLA (ante la duda, denegar): un código de negocio
     * que corre sin gimnasio es un bug, y es mejor un error que tocar datos de quién sabe quién.
     */
    public static GimnasioActual actual() {
        if (!ACTUAL.isBound()) {
            throw new IllegalStateException("No hay gimnasio en el contexto: operación denegada");
        }
        return ACTUAL.get();
    }

    /** Para código que puede correr con o sin gimnasio (ej. logs). */
    public static Optional<GimnasioActual> siHay() {
        return ACTUAL.isBound() ? Optional.of(ACTUAL.get()) : Optional.empty();
    }

    /** Ejecuta {@code operacion} con {@code gimnasio} como contexto; al terminar, el contexto desaparece. */
    public static <T> T ejecutarPara(GimnasioActual gimnasio, Callable<T> operacion) throws Exception {
        return ScopedValue.where(ACTUAL, gimnasio).call(operacion::call);
    }
}
