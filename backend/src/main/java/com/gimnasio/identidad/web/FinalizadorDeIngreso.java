package com.gimnasio.identidad.web;

import java.time.Clock;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import com.gimnasio.identidad.mfa.RequisitoMfa;
import com.gimnasio.identidad.mfa.ServicioMfa;
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
    private final ServicioMfa mfa;
    private final Clock reloj;

    FinalizadorDeIngreso(SessionAuthenticationStrategy estrategiaAlIngresar, SecurityContextRepository contextos,
            ServicioMfa mfa, Clock reloj) {
        this.estrategiaAlIngresar = estrategiaAlIngresar;
        this.contextos = contextos;
        this.mfa = mfa;
        this.reloj = reloj;
    }

    /**
     * Después del PRIMER factor (contraseña o código por correo, ADR-0006 §2): si ningún rol exige MFA,
     * termina el ingreso (204); si alguno lo exige, deja el ingreso pendiente y responde qué paso falta (200).
     */
    ResponseEntity<?> trasPrimerFactor(String email, Authentication autenticado, HttpServletRequest request,
            HttpServletResponse response) {
        RequisitoMfa requisito = mfa.requisitoPara(email);
        if (requisito == RequisitoMfa.NO_REQUERIDO) {
            finalizar(autenticado, request, response);
            return ResponseEntity.noContent().build();
        }
        // Falta el segundo factor: la sesión solo recuerda "pasó el primero" (5 min), sin autenticar.
        IngresoPendiente.guardar(request, email, requisito, reloj.millis());
        return ResponseEntity.ok(Map.of("paso", requisito == RequisitoMfa.CODIGO ? "CODIGO_MFA" : "CONFIGURAR_MFA"));
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
