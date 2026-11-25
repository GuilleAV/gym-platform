package com.gimnasio.inscripcion.web;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gimnasio.inscripcion.DatosFicha;
import com.gimnasio.inscripcion.DocumentoAceptado;
import com.gimnasio.inscripcion.FichaNoEditable;
import com.gimnasio.inscripcion.ResultadoEnvio;
import com.gimnasio.inscripcion.ServicioFicha;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Completar la ficha con la invitación (UC-05). Públicos en SeguridadConfig, pero cada uno exige una
 * {@link SesionDeFicha}: sin haber abierto antes una invitación válida, responden 401.
 *
 * <ul>
 *   <li>{@code POST /api/invitacion/abrir} {"token"} → la ficha / 404 si la invitación no sirve</li>
 *   <li>{@code GET /api/ficha} y {@code PUT /api/ficha} (borrador)</li>
 *   <li>{@code POST /api/ficha/enviar} {"datos", "documentos"} → {"estado": "ACTIVO" | "EN_REVISION"} / 400</li>
 * </ul>
 */
@RestController
class FichaController {

    /** Mismo mensaje para inexistente, usada o vencida: no le da pistas a quien prueba tokens. */
    private static final Map<String, Object> INVITACION_INVALIDA = Map.of("error", "La invitación no es válida o ya venció");

    private final ServicioFicha fichas;
    private final Clock reloj;

    FichaController(ServicioFicha fichas, Clock reloj) {
        this.fichas = fichas;
        this.reloj = reloj;
    }

    record Token(String token) {

        @Override
        public String toString() {
            return "Token[***]";
        }
    }

    record Envio(DatosFicha datos, List<DocumentoAceptado> documentos) {
    }

    @PostMapping("/api/invitacion/abrir")
    ResponseEntity<?> abrir(@RequestBody(required = false) Token cuerpo, HttpServletRequest request) {
        Optional<Long> inscripcion = cuerpo == null ? Optional.empty() : fichas.inscripcionDeInvitacion(cuerpo.token());
        if (inscripcion.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(INVITACION_INVALIDA);
        }
        SesionDeFicha.abrir(request, inscripcion.get(), reloj.millis());
        return ResponseEntity.of(fichas.leer(inscripcion.get()));
    }

    @GetMapping("/api/ficha")
    ResponseEntity<?> leer(HttpServletRequest request) {
        return SesionDeFicha.inscripcion(request, reloj.millis()).<ResponseEntity<?>>map(id -> ResponseEntity.of(fichas.leer(id)))
                .orElseGet(FichaController::noAutorizado);
    }

    @PutMapping("/api/ficha")
    ResponseEntity<?> guardar(@RequestBody(required = false) DatosFicha datos, HttpServletRequest request) {
        Optional<Long> id = SesionDeFicha.inscripcion(request, reloj.millis());
        if (id.isEmpty()) {
            return noAutorizado();
        }
        DatosFicha recibidos = datos == null ? DatosFicha.vacia() : datos;
        List<String> errores = recibidos.erroresDeFormato();
        if (!errores.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errores", errores));
        }
        fichas.guardarBorrador(id.get(), recibidos);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/ficha/enviar")
    ResponseEntity<?> enviar(@RequestBody(required = false) Envio envio, HttpServletRequest request) {
        Optional<Long> id = SesionDeFicha.inscripcion(request, reloj.millis());
        if (id.isEmpty()) {
            return noAutorizado();
        }
        Envio e = envio == null ? new Envio(DatosFicha.vacia(), List.of()) : envio;
        ResultadoEnvio resultado = fichas.enviar(id.get(), e.datos() == null ? DatosFicha.vacia() : e.datos(), e.documentos(),
                ServicioFicha.Medio.INVITACION, "invitacion", null, request.getRemoteAddr());
        if (!resultado.enviada()) {
            return ResponseEntity.badRequest().body(Map.of("errores", resultado.errores()));
        }
        SesionDeFicha.cerrar(request); // terminó: la sesión de ficha ya no sirve para nada
        return ResponseEntity.ok(Map.of("estado", resultado.estado()));
    }

    @ExceptionHandler(FichaNoEditable.class)
    ResponseEntity<?> noEditable(FichaNoEditable e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    private static ResponseEntity<?> noAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Abrí de nuevo el enlace de tu invitación"));
    }
}
