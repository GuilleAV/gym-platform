package com.gimnasio.identidad.seguridad;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gimnasio.plataforma.ContextoGimnasio;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Defensa en profundidad (ADR-0002 §1): la sesión recuerda en qué gimnasio se inició; si llega en
 * un request de OTRO gimnasio, se invalida y se responde 401.
 *
 * <p>En condiciones normales esto no puede pasar (la sesión está en la base de su gimnasio y en otra
 * base "no existe"). Este filtro está para el día que un cambio futuro rompa esa garantía: si eso
 * ocurre, se deniega en vez de mezclar gimnasios.</p>
 */
public class FiltroGimnasioDeLaSesion extends OncePerRequestFilter {

    /** Atributo de sesión con el id del gimnasio donde se inició sesión. */
    public static final String ATRIBUTO_GIMNASIO = "gym.gimnasioId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        HttpSession sesion = request.getSession(false);
        if (sesion != null && autenticacion != null && autenticacion.isAuthenticated()
                && !(autenticacion instanceof AnonymousAuthenticationToken)) {
            Object gimnasioDeLaSesion = sesion.getAttribute(ATRIBUTO_GIMNASIO);
            long gimnasioDelRequest = ContextoGimnasio.actual().id();
            if (!(gimnasioDeLaSesion instanceof Long id) || id != gimnasioDelRequest) {
                sesion.invalidate();
                SecurityContextHolder.clearContext();
                RespuestasDeSeguridad.noAutorizado(response);
                return;
            }
        }
        cadena.doFilter(request, response);
    }
}
