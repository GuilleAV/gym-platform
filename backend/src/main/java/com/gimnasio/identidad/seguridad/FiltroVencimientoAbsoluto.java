package com.gimnasio.identidad.seguridad;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Vencimiento <b>absoluto</b> de la sesión (paso 0.6b): pasadas N horas desde el ingreso, la sesión se
 * cierra aunque se haya usado sin parar.
 *
 * <p><b>Por qué hace falta además del vencimiento por inactividad:</b> el de inactividad
 * ({@code spring.session.timeout}) se renueva con cada request. Si alguien roba una cookie de sesión y
 * la usa cada pocos minutos, la sesión viviría para siempre. Con el vencimiento absoluto, una cookie
 * robada deja de servir como máximo N horas después del ingreso.</p>
 *
 * <p>Una sesión autenticada SIN fecha de ingreso (creada antes de este cambio, o por un bug) se trata
 * como vencida: ante la duda, denegar.</p>
 */
public class FiltroVencimientoAbsoluto extends OncePerRequestFilter {

    /** Atributo de sesión con el instante del ingreso, en milisegundos desde 1970 (UTC). */
    public static final String ATRIBUTO_INGRESO = "gym.ingresoEnMs";

    private final Clock reloj;
    private final Duration vencimiento;

    public FiltroVencimientoAbsoluto(Clock reloj, Duration vencimiento) {
        this.reloj = reloj;
        this.vencimiento = vencimiento;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        HttpSession sesion = request.getSession(false);
        if (sesion != null && autenticacion != null && autenticacion.isAuthenticated()
                && !(autenticacion instanceof AnonymousAuthenticationToken)) {
            Object ingreso = sesion.getAttribute(ATRIBUTO_INGRESO);
            if (!(ingreso instanceof Long ingresoMs) || reloj.millis() - ingresoMs >= vencimiento.toMillis()) {
                sesion.invalidate();
                SecurityContextHolder.clearContext();
                RespuestasDeSeguridad.noAutorizado(response);
                return;
            }
        }
        cadena.doFilter(request, response);
    }
}
