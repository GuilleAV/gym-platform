package com.gimnasio.identidad.web;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
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

import com.gimnasio.identidad.mfa.ConfiguracionMfa;
import com.gimnasio.identidad.mfa.RequisitoMfa;
import com.gimnasio.identidad.mfa.ServicioMfa;
import com.gimnasio.identidad.seguridad.LimitadorDeIntentos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Segundo paso del ingreso (ADR-0005 §4). Todos estos endpoints exigen un {@link IngresoPendiente}:
 * sin haber pasado antes la contraseña en este mismo gimnasio, responden 401.
 *
 * <ul>
 *   <li>{@code POST /api/sesion/mfa/verificar} {"codigo": "123456"} → 204 + sesión autenticada.
 *       También acepta un código de recuperación ("ABCDE-FGHJK").</li>
 *   <li>{@code POST /api/sesion/mfa/configurar} → {"secreto", "uri"} para dibujar el QR.</li>
 *   <li>{@code POST /api/sesion/mfa/confirmar} {"codigo": "123456"} → {"codigosRecuperacion": [...]} +
 *       sesión autenticada.</li>
 * </ul>
 */
@RestController
class MfaController {

    private final ServicioMfa mfa;
    private final LimitadorDeIntentos limitador;
    private final FinalizadorDeIngreso finalizador;
    private final UserDetailsService usuarios;
    private final Clock reloj;

    MfaController(ServicioMfa mfa, LimitadorDeIntentos limitador, FinalizadorDeIngreso finalizador,
            UserDetailsService usuarios, Clock reloj) {
        this.mfa = mfa;
        this.limitador = limitador;
        this.finalizador = finalizador;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    record Codigo(String codigo) {

        @Override
        public String toString() {
            return "Codigo[***]";
        }
    }

    @PostMapping("/api/sesion/mfa/verificar")
    ResponseEntity<?> verificar(@RequestBody(required = false) Codigo cuerpo, HttpServletRequest request,
            HttpServletResponse response) {
        Optional<IngresoPendiente> pendiente = pendiente(request, RequisitoMfa.CODIGO);
        if (pendiente.isEmpty()) {
            return noAutorizado();
        }
        if (cuerpo == null || cuerpo.codigo() == null || cuerpo.codigo().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String email = pendiente.get().email();
        String ip = request.getRemoteAddr();
        // Un código de 6 dígitos tiene 1.000.000 de combinaciones: sin límite, se adivina. Mismo límite que la clave.
        Optional<Duration> bloqueo = limitador.bloqueoVigente(email, ip);
        if (bloqueo.isPresent()) {
            return SesionController.demasiadosIntentos(bloqueo.get());
        }
        if (!mfa.verificar(email, cuerpo.codigo())) {
            limitador.registrarFallo(email, ip);
            return noAutorizado();
        }
        limitador.registrarExito(email);
        return finalizar(email, request, response).<ResponseEntity<?>>map(ok -> ResponseEntity.noContent().build())
                .orElseGet(MfaController::noAutorizado);
    }

    @PostMapping("/api/sesion/mfa/configurar")
    ResponseEntity<?> configurar(HttpServletRequest request) {
        Optional<IngresoPendiente> pendiente = pendiente(request, RequisitoMfa.CONFIGURAR);
        if (pendiente.isEmpty()) {
            return noAutorizado();
        }
        try {
            ConfiguracionMfa configuracion = mfa.iniciarConfiguracion(pendiente.get().email());
            return ResponseEntity.ok(Map.of("secreto", configuracion.secreto(), "uri", configuracion.uri()));
        } catch (IllegalStateException e) {
            return noAutorizado(); // ya estaba configurado (otro request ganó): que vuelva a ingresar
        }
    }

    @PostMapping("/api/sesion/mfa/confirmar")
    ResponseEntity<?> confirmar(@RequestBody(required = false) Codigo cuerpo, HttpServletRequest request,
            HttpServletResponse response) {
        Optional<IngresoPendiente> pendiente = pendiente(request, RequisitoMfa.CONFIGURAR);
        if (pendiente.isEmpty()) {
            return noAutorizado();
        }
        if (cuerpo == null || cuerpo.codigo() == null || cuerpo.codigo().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String email = pendiente.get().email();
        String ip = request.getRemoteAddr();
        Optional<Duration> bloqueo = limitador.bloqueoVigente(email, ip);
        if (bloqueo.isPresent()) {
            return SesionController.demasiadosIntentos(bloqueo.get());
        }
        Optional<List<String>> codigos = mfa.confirmarConfiguracion(email, cuerpo.codigo());
        if (codigos.isEmpty()) {
            limitador.registrarFallo(email, ip);
            return noAutorizado();
        }
        limitador.registrarExito(email);
        return finalizar(email, request, response)
                .<ResponseEntity<?>>map(ok -> ResponseEntity.ok(Map.of("codigosRecuperacion", codigos.get())))
                .orElseGet(MfaController::noAutorizado);
    }

    private Optional<IngresoPendiente> pendiente(HttpServletRequest request, RequisitoMfa esperado) {
        return IngresoPendiente.leer(request, reloj.millis()).filter(p -> p.requisito() == esperado);
    }

    /**
     * Vuelve a cargar el usuario antes de autenticar: si lo suspendieron entre la contraseña y el código,
     * no entra.
     */
    private Optional<Boolean> finalizar(String email, HttpServletRequest request, HttpServletResponse response) {
        UserDetails usuario;
        try {
            usuario = usuarios.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            return Optional.empty();
        }
        if (!usuario.isEnabled()) {
            IngresoPendiente.borrar(request);
            return Optional.empty();
        }
        finalizador.finalizar(UsernamePasswordAuthenticationToken.authenticated(usuario.getUsername(), null,
                usuario.getAuthorities()), request, response);
        return Optional.of(Boolean.TRUE);
    }

    private static ResponseEntity<Map<String, Object>> noAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(SesionController.CREDENCIALES_INVALIDAS);
    }
}
