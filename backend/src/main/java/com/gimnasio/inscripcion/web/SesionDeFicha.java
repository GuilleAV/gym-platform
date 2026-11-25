package com.gimnasio.inscripcion.web;

import java.time.Duration;
import java.util.Optional;

import com.gimnasio.plataforma.ContextoGimnasio;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * "Esta persona abrió una invitación válida": permite ver y completar SU ficha, y nada más.
 *
 * <p>Como el ingreso pendiente del MFA, vive en una sesión <b>sin autenticar</b>: para el resto del sistema
 * la persona sigue siendo anónima. Dura 2 horas y solo vale en el gimnasio donde se abrió.</p>
 */
final class SesionDeFicha {

    static final Duration VIGENCIA = Duration.ofHours(2);

    private static final String INSCRIPCION = "gym.ficha.inscripcionId";
    private static final String GIMNASIO = "gym.ficha.gimnasioId";
    private static final String DESDE = "gym.ficha.desdeMs";

    private SesionDeFicha() {
    }

    static void abrir(HttpServletRequest request, long inscripcionId, long ahoraMs) {
        if (request.getSession(false) != null) {
            request.changeSessionId(); // contra la fijación de sesión
        }
        HttpSession sesion = request.getSession(true);
        sesion.setAttribute(INSCRIPCION, inscripcionId);
        sesion.setAttribute(GIMNASIO, ContextoGimnasio.actual().id());
        sesion.setAttribute(DESDE, ahoraMs);
    }

    static Optional<Long> inscripcion(HttpServletRequest request, long ahoraMs) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null || !(sesion.getAttribute(INSCRIPCION) instanceof Long id)
                || !(sesion.getAttribute(GIMNASIO) instanceof Long gimnasio)
                || !(sesion.getAttribute(DESDE) instanceof Long desde)) {
            return Optional.empty();
        }
        if (gimnasio != ContextoGimnasio.actual().id() || ahoraMs - desde < 0 || ahoraMs - desde >= VIGENCIA.toMillis()) {
            cerrar(request);
            return Optional.empty();
        }
        return Optional.of(id);
    }

    static void cerrar(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.removeAttribute(INSCRIPCION);
            sesion.removeAttribute(GIMNASIO);
            sesion.removeAttribute(DESDE);
        }
    }
}
