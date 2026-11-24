package com.gimnasio.identidad.web;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
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
import com.gimnasio.identidad.seguridad.FiltroVencimientoAbsoluto;
import com.gimnasio.identidad.seguridad.LimitadorDeIntentos;
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

    /** Correo o IP bloqueados por demasiados fallos. Mismo cuerpo exista o no el correo. */
    private static final Map<String, Object> DEMASIADOS_INTENTOS =
            Map.of("type", "about:blank", "title", "Too Many Requests", "status", 429);

    private final AuthenticationManager autenticacion;
    private final SessionAuthenticationStrategy estrategiaAlIngresar;
    private final SecurityContextRepository contextos;
    private final ServicioUsuarios usuarios;
    private final LimitadorDeIntentos limitador;
    private final Clock reloj;

    SesionController(AuthenticationManager autenticacion, SessionAuthenticationStrategy estrategiaAlIngresar,
            SecurityContextRepository contextos, ServicioUsuarios usuarios, LimitadorDeIntentos limitador, Clock reloj) {
        this.autenticacion = autenticacion;
        this.estrategiaAlIngresar = estrategiaAlIngresar;
        this.contextos = contextos;
        this.usuarios = usuarios;
        this.limitador = limitador;
        this.reloj = reloj;
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

    /**
     * {@code POST /api/sesion/ingresar}: {"email": "...", "clave": "..."} → 204 + cookie de sesión.
     *
     * <p>Respuestas: 204 si ingresó; 401 si el correo o la clave no son válidos (sin decir cuál); 429 con
     * {@code Retry-After} si el correo o la IP están bloqueados por demasiados fallos; 400 si faltan datos.</p>
     */
    @PostMapping("/api/sesion/ingresar")
    ResponseEntity<?> ingresar(@RequestBody Credenciales credenciales, HttpServletRequest request,
            HttpServletResponse response) {
        if (credenciales == null || credenciales.email() == null || credenciales.email().isBlank()
                || credenciales.clave() == null || credenciales.clave().length() > Contrasenias.LARGO_MAXIMO) {
            return ResponseEntity.badRequest().build();
        }
        String email = ServicioUsuarios.normalizarEmail(credenciales.email());
        String ip = request.getRemoteAddr();

        // Bloqueado: se responde SIN comparar la clave. Así, mientras dura el bloqueo, probar claves no
        // sirve de nada (ni siquiera la correcta entra) y no se gasta CPU en Argon2id.
        Optional<Duration> bloqueo = limitador.bloqueoVigente(email, ip);
        if (bloqueo.isPresent()) {
            return demasiadosIntentos(bloqueo.get());
        }

        Authentication autenticado;
        try {
            autenticado = autenticacion.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email,
                    credenciales.clave()));
        } catch (AuthenticationException e) {
            limitador.registrarFallo(email, ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(CREDENCIALES_INVALIDAS);
        }
        limitador.registrarExito(email);

        estrategiaAlIngresar.onAuthentication(autenticado, request, response);
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticado);
        SecurityContextHolder.setContext(contexto);
        request.getSession().setAttribute(FiltroGimnasioDeLaSesion.ATRIBUTO_GIMNASIO, ContextoGimnasio.actual().id());
        request.getSession().setAttribute(FiltroVencimientoAbsoluto.ATRIBUTO_INGRESO, reloj.millis());
        contextos.saveContext(contexto, request, response);
        return ResponseEntity.noContent().build();
    }

    /** 429 + Retry-After en segundos (redondeado hacia arriba: nunca decir "ya podés" antes de tiempo). */
    private static ResponseEntity<Map<String, Object>> demasiadosIntentos(Duration falta) {
        long segundos = Math.max(1, (falta.toMillis() + 999) / 1000);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(segundos))
                .body(DEMASIADOS_INTENTOS);
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
