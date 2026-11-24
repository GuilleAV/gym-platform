package com.gimnasio.identidad.web;

import java.time.Duration;
import java.util.Optional;

import com.gimnasio.identidad.mfa.RequisitoMfa;
import com.gimnasio.plataforma.ContextoGimnasio;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * "Pasó la contraseña, falta el segundo factor" (ADR-0005 §4).
 *
 * <p>Se guarda en una sesión <b>sin autenticar</b>: para Spring Security el usuario sigue siendo anónimo
 * y no puede usar ningún endpoint privado. Solo sirve para los endpoints de MFA, durante 5 minutos y en
 * el mismo gimnasio donde se ingresó la contraseña.</p>
 *
 * <p>Se guarda como atributos simples (String y Long) y no como un objeto propio: Spring Session los
 * serializa en la base, y una clase propia podría dejar de deserializarse si mañana cambia.</p>
 */
record IngresoPendiente(String email, RequisitoMfa requisito) {

    static final Duration VIGENCIA = Duration.ofMinutes(5);

    private static final String EMAIL = "gym.mfa.email";
    private static final String REQUISITO = "gym.mfa.requisito";
    private static final String DESDE = "gym.mfa.desdeMs";
    private static final String GIMNASIO = "gym.mfa.gimnasioId";

    /** Guarda el paso pendiente en una sesión con ID nuevo (contra la fijación de sesión). */
    static void guardar(HttpServletRequest request, String email, RequisitoMfa requisito, long ahoraMs) {
        HttpSession existente = request.getSession(false);
        if (existente != null) {
            request.changeSessionId();
        }
        HttpSession sesion = request.getSession(true);
        sesion.setAttribute(EMAIL, email);
        sesion.setAttribute(REQUISITO, requisito.name());
        sesion.setAttribute(DESDE, ahoraMs);
        sesion.setAttribute(GIMNASIO, ContextoGimnasio.actual().id());
    }

    /** El paso pendiente, si existe, es de este gimnasio y no venció. Si venció, lo borra. */
    static Optional<IngresoPendiente> leer(HttpServletRequest request, long ahoraMs) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null) {
            return Optional.empty();
        }
        Object email = sesion.getAttribute(EMAIL);
        Object requisito = sesion.getAttribute(REQUISITO);
        Object desde = sesion.getAttribute(DESDE);
        Object gimnasio = sesion.getAttribute(GIMNASIO);
        if (!(email instanceof String correo) || !(requisito instanceof String req) || !(desde instanceof Long desdeMs)
                || !(gimnasio instanceof Long gimnasioId)) {
            return Optional.empty();
        }
        if (!vigente(desdeMs, ahoraMs) || gimnasioId != ContextoGimnasio.actual().id()) {
            borrar(request);
            return Optional.empty();
        }
        return Optional.of(new IngresoPendiente(correo, RequisitoMfa.valueOf(req)));
    }

    static void borrar(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.removeAttribute(EMAIL);
            sesion.removeAttribute(REQUISITO);
            sesion.removeAttribute(DESDE);
            sesion.removeAttribute(GIMNASIO);
        }
    }

    /** Pura, para testear el vencimiento sin esperar 5 minutos. */
    static boolean vigente(long desdeMs, long ahoraMs) {
        long transcurrido = ahoraMs - desdeMs;
        return transcurrido >= 0 && transcurrido < VIGENCIA.toMillis();
    }
}
