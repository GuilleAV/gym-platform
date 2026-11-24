package com.gimnasio.identidad.web;

import java.time.Clock;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import com.gimnasio.identidad.seguridad.FiltroGimnasioDeLaSesion;
import com.gimnasio.identidad.seguridad.FiltroVencimientoAbsoluto;
import com.gimnasio.plataforma.ContextoGimnasio;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * El último paso de todo ingreso, con o sin MFA: recién acá la sesión queda AUTENTICADA.
 *
 * <p>Está en un solo lugar para que el ingreso con contraseña y el ingreso con MFA no puedan divergir
 * (por ejemplo, que uno se olvide de cambiar el ID de sesión o de anotar el gimnasio).</p>
 */
@Component
class FinalizadorDeIngreso {

    private final SessionAuthenticationStrategy estrategiaAlIngresar;
    private final SecurityContextRepository contextos;
    private final Clock reloj;

    FinalizadorDeIngreso(SessionAuthenticationStrategy estrategiaAlIngresar, SecurityContextRepository contextos,
            Clock reloj) {
        this.estrategiaAlIngresar = estrategiaAlIngresar;
        this.contextos = contextos;
        this.reloj = reloj;
    }

    void finalizar(Authentication autenticado, HttpServletRequest request, HttpServletResponse response) {
        IngresoPendiente.borrar(request);
        // Nuevo ID de sesión (contra session fixation) y nuevo token CSRF.
        estrategiaAlIngresar.onAuthentication(autenticado, request, response);
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticado);
        SecurityContextHolder.setContext(contexto);
        request.getSession().setAttribute(FiltroGimnasioDeLaSesion.ATRIBUTO_GIMNASIO, ContextoGimnasio.actual().id());
        request.getSession().setAttribute(FiltroVencimientoAbsoluto.ATRIBUTO_INGRESO, reloj.millis());
        contextos.saveContext(contexto, request, response);
    }
}
