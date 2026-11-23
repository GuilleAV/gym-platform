package com.gimnasio.identidad.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gimnasio.identidad.Contrasenias;
import com.gimnasio.identidad.ServicioUsuarios;
import com.gimnasio.identidad.seguridad.FiltroGimnasioDeLaSesion;
import com.gimnasio.plataforma.ContextoGimnasio;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Inicio de sesión, consulta de la sesión y token CSRF. El cierre de sesión ({@code POST
 * /api/sesion/salir}) lo atiende Spring Security directamente.
 */
@RestController
class SesionController {

    /** Misma respuesta para correo inexistente, clave incorrecta o cuenta suspendida: no damos pistas. */
    private static final Map<String, Object> CREDENCIALES_INVALIDAS =
            Map.of("type", "about:blank", "title", "Unauthorized", "status", 401);

    private final AuthenticationManager autenticacion;
    private final SessionAuthenticationStrategy estrategiaAlIngresar;
    private final SecurityContextRepository contextos;
    private final ServicioUsuarios usuarios;

    SesionController(AuthenticationManager autenticacion, SessionAuthenticationStrategy estrategiaAlIngresar,
            SecurityContextRepository contextos, ServicioUsuarios usuarios) {
        this.autenticacion = autenticacion;
        this.estrategiaAlIngresar = estrategiaAlIngresar;
        this.contextos = contextos;
        this.usuarios = usuarios;
    }

    record Credenciales(String email, String clave) {

        /** Evita que la clave aparezca en un log si alguien imprime el objeto. */
        @Override
        public String toString() {
            return "Credenciales[email=" + email + ", clave=***]";
        }
    }

    /** {@code GET /api/sesion/csrf}: genera la cookie del token CSRF (la app Angular lo llama al arrancar). */
    @GetMapping("/api/sesion/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken(); // pedir el valor fuerza a generarlo y escribir la cookie
        return ResponseEntity.noContent().build();
    }

    /** {@code POST /api/sesion/ingresar}: {"email": "...", "clave": "..."} → 204 + cookie de sesión. */
    @PostMapping("/api/sesion/ingresar")
    ResponseEntity<?> ingresar(@RequestBody Credenciales credenciales, HttpServletRequest request,
            HttpServletResponse response) {
        if (credenciales == null || credenciales.email() == null || credenciales.email().isBlank()
                || credenciales.clave() == null || credenciales.clave().length() > Contrasenias.LARGO_MAXIMO) {
            return ResponseEntity.badRequest().build();
        }
        Authentication autenticado;
        try {
            autenticado = autenticacion.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    ServicioUsuarios.normalizarEmail(credenciales.email()), credenciales.clave()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(CREDENCIALES_INVALIDAS);
        }

        estrategiaAlIngresar.onAuthentication(autenticado, request, response);
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticado);
        SecurityContextHolder.setContext(contexto);
        request.getSession().setAttribute(FiltroGimnasioDeLaSesion.ATRIBUTO_GIMNASIO, ContextoGimnasio.actual().id());
        contextos.saveContext(contexto, request, response);
        return ResponseEntity.noContent().build();
    }

    /** {@code GET /api/sesion/yo}: quién está conectado (requiere sesión). */
    @GetMapping("/api/sesion/yo")
    Map<String, String> yo(Authentication autenticado) {
        return Map.of(
                "email", autenticado.getName(),
                "nombre", usuarios.nombreDe(autenticado.getName()).orElse(""),
                "gimnasio", ContextoGimnasio.actual().slug());
    }
}
