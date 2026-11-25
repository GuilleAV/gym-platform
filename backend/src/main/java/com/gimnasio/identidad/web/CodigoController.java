package com.gimnasio.identidad.web;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gimnasio.identidad.ServicioUsuarios;
import com.gimnasio.identidad.codigo.ServicioCodigoDeIngreso;
import com.gimnasio.identidad.seguridad.LimitadorDeIntentos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Ingreso con código por correo (ADR-0006).
 *
 * <ul>
 *   <li>{@code POST /api/sesion/codigo/solicitar} {"email"} → <b>siempre</b> 202, exista o no el correo.</li>
 *   <li>{@code POST /api/sesion/codigo/ingresar} {"email", "codigo"} → como el ingreso con contraseña:
 *       204 adentro; 200 {"paso"} si el rol exige MFA; 401 código incorrecto o vencido; 429 bloqueado.</li>
 * </ul>
 */
@RestController
class CodigoController {

    /** Mismo cuerpo siempre: la respuesta no puede servir para averiguar qué correos están registrados. */
    private static final Map<String, Object> SOLICITADO = Map.of("estado", "Si el correo está registrado, te enviamos un código.");

    private final ServicioCodigoDeIngreso codigos;
    private final LimitadorDeIntentos limitador;
    private final FinalizadorDeIngreso finalizador;
    private final UserDetailsService usuarios;

    CodigoController(ServicioCodigoDeIngreso codigos, LimitadorDeIntentos limitador, FinalizadorDeIngreso finalizador,
            UserDetailsService usuarios) {
        this.codigos = codigos;
        this.limitador = limitador;
        this.finalizador = finalizador;
        this.usuarios = usuarios;
    }

    record Solicitud(String email) {
    }

    record IngresoConCodigo(String email, String codigo) {

        @Override
        public String toString() {
            return "IngresoConCodigo[email=***, codigo=***]";
        }
    }

    @PostMapping("/api/sesion/codigo/solicitar")
    ResponseEntity<?> solicitar(@RequestBody(required = false) Solicitud solicitud) {
        if (solicitud == null || solicitud.email() == null || solicitud.email().isBlank()
                || solicitud.email().length() > 254 || !solicitud.email().contains("@")) {
            return ResponseEntity.badRequest().build();
        }
        codigos.solicitar(solicitud.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(SOLICITADO);
    }

    @PostMapping("/api/sesion/codigo/ingresar")
    ResponseEntity<?> ingresar(@RequestBody(required = false) IngresoConCodigo cuerpo, HttpServletRequest request,
            HttpServletResponse response) {
        if (cuerpo == null || cuerpo.email() == null || cuerpo.email().isBlank() || cuerpo.codigo() == null
                || cuerpo.codigo().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String email = ServicioUsuarios.normalizarEmail(cuerpo.email());
        String ip = request.getRemoteAddr();
        Optional<Duration> bloqueo = limitador.bloqueoVigente(email, ip);
        if (bloqueo.isPresent()) {
            return SesionController.demasiadosIntentos(bloqueo.get());
        }
        if (!codigos.verificar(email, cuerpo.codigo())) {
            limitador.registrarFallo(email, ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(SesionController.CREDENCIALES_INVALIDAS);
        }
        limitador.registrarExito(email);

        UserDetails usuario;
        try {
            usuario = usuarios.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(SesionController.CREDENCIALES_INVALIDAS);
        }
        if (!usuario.isEnabled()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(SesionController.CREDENCIALES_INVALIDAS);
        }
        return finalizador.trasPrimerFactor(email,
                UsernamePasswordAuthenticationToken.authenticated(usuario.getUsername(), null, usuario.getAuthorities()),
                request, response);
    }
}
