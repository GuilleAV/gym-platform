package com.gimnasio.inscripcion.web;

import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gimnasio.auditoria.Auditoria;
import com.gimnasio.identidad.ServicioUsuarios;
import com.gimnasio.inscripcion.CorreoYaRegistrado;
import com.gimnasio.inscripcion.DatosFicha;
import com.gimnasio.inscripcion.DatosMostrador;
import com.gimnasio.inscripcion.DocumentoAceptado;
import com.gimnasio.inscripcion.FichaNoEditable;
import com.gimnasio.inscripcion.InscripcionCargada;
import com.gimnasio.inscripcion.ResultadoEnvio;
import com.gimnasio.inscripcion.ResumenInscripcion;
import com.gimnasio.inscripcion.ServicioFicha;
import com.gimnasio.inscripcion.ServicioInscripcion;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Inscripción en el mostrador (ADR-0007). Solo para los roles ADMINISTRADOR y RECEPCION: la regla está en
 * SeguridadConfig, que es el único lugar donde se decide quién entra a cada URL.
 *
 * <ul>
 *   <li>{@code POST /api/inscripciones} → 201 / 400 (con la lista de errores) / 409 correo ya registrado</li>
 *   <li>{@code GET /api/inscripciones?estado=FICHA_PENDIENTE} → lista</li>
 *   <li>{@code POST /api/inscripciones/{id}/reenviar-invitacion} → 202 / 404 / 409</li>
 * </ul>
 */
@RestController
class InscripcionController {

    private static final Set<String> ESTADOS = Set.of("PENDIENTE", "FICHA_PENDIENTE", "EN_REVISION", "APROBADA", "RECHAZADA");

    private final ServicioInscripcion servicio;
    private final ServicioFicha fichas;
    private final ServicioUsuarios usuarios;

    InscripcionController(ServicioInscripcion servicio, ServicioFicha fichas, ServicioUsuarios usuarios) {
        this.servicio = servicio;
        this.fichas = fichas;
        this.usuarios = usuarios;
    }

    record Pedido(String nombre, String apellido, String email, String celular, LocalDate fechaNacimiento) {
    }

    @PostMapping("/api/inscripciones")
    ResponseEntity<?> inscribir(@RequestBody(required = false) Pedido pedido, Principal quien, HttpServletRequest request) {
        if (pedido == null) {
            return ResponseEntity.badRequest().body(Map.of("errores", List.of("faltan los datos")));
        }
        DatosMostrador datos = new DatosMostrador(pedido.nombre(), pedido.apellido(), pedido.email(), pedido.celular(),
                pedido.fechaNacimiento());
        List<String> errores = datos.errores(servicio.hoy());
        if (!errores.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errores", errores));
        }
        InscripcionCargada cargada = servicio.inscribirEnMostrador(datos, quien.getName(), urlBase(request));
        return ResponseEntity.created(URI.create("/api/inscripciones/" + cargada.id())).body(cargada);
    }

    @GetMapping("/api/inscripciones")
    ResponseEntity<?> listar(@RequestParam(required = false) String estado) {
        if (estado != null && !ESTADOS.contains(estado)) {
            return ResponseEntity.badRequest().body(Map.of("errores", List.of("estado desconocido")));
        }
        List<ResumenInscripcion> lista = servicio.listar(estado);
        return ResponseEntity.ok(lista);
    }

    @PostMapping("/api/inscripciones/{id}/reenviar-invitacion")
    ResponseEntity<?> reenviar(@PathVariable long id, Principal quien, HttpServletRequest request) {
        return switch (servicio.reenviarInvitacion(id, quien.getName(), urlBase(request))) {
            case ENVIADA -> ResponseEntity.accepted().build();
            case NO_ENCONTRADA -> ResponseEntity.notFound().build();
            case SIN_CORREO -> conflicto("La inscripción no tiene correo: la ficha se completa en el mostrador");
            case NO_CORRESPONDE -> conflicto("La ficha ya no está pendiente");
            case MUY_PRONTO -> conflicto("Esperá un minuto antes de volver a enviarla");
        };
    }

    // ----------------------------------------------- ficha completada en el mostrador

    record EnvioMostrador(DatosFicha datos, List<DocumentoAceptado> documentos) {
    }

    @GetMapping("/api/inscripciones/{id}/ficha")
    ResponseEntity<?> leerFicha(@PathVariable long id) {
        return ResponseEntity.of(fichas.leer(id));
    }

    @PutMapping("/api/inscripciones/{id}/ficha")
    ResponseEntity<?> guardarFicha(@PathVariable long id, @RequestBody(required = false) DatosFicha datos) {
        DatosFicha recibidos = datos == null ? DatosFicha.vacia() : datos;
        List<String> errores = recibidos.erroresDeFormato();
        if (!errores.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errores", errores));
        }
        fichas.guardarBorrador(id, recibidos);
        return ResponseEntity.noContent().build();
    }

    /**
     * La recepción completa la ficha (típicamente de quien no tiene correo). Registra que los documentos se
     * aceptaron EN EL MOSTRADOR y quién del personal lo registró.
     */
    @PostMapping("/api/inscripciones/{id}/ficha/enviar")
    ResponseEntity<?> enviarFicha(@PathVariable long id, @RequestBody(required = false) EnvioMostrador envio, Principal quien) {
        EnvioMostrador e = envio == null ? new EnvioMostrador(DatosFicha.vacia(), List.of()) : envio;
        Long actorId = usuarios.idDe(quien.getName()).orElse(null);
        ResultadoEnvio resultado = fichas.enviar(id, e.datos() == null ? DatosFicha.vacia() : e.datos(), e.documentos(),
                ServicioFicha.Medio.MOSTRADOR, actorId == null ? "desconocido" : Auditoria.usuario(actorId), actorId, null);
        if (!resultado.enviada()) {
            return ResponseEntity.badRequest().body(Map.of("errores", resultado.errores()));
        }
        return ResponseEntity.ok(Map.of("estado", resultado.estado()));
    }

    @ExceptionHandler(FichaNoEditable.class)
    ResponseEntity<?> fichaNoEditable(FichaNoEditable e) {
        return conflicto(e.getMessage());
    }

    @ExceptionHandler(CorreoYaRegistrado.class)
    ResponseEntity<?> correoYaRegistrado(CorreoYaRegistrado e) {
        return conflicto(e.getMessage());
    }

    /** Dos cargas simultáneas con el mismo correo: la base (UNIQUE) frena la segunda. */
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<?> duplicado(DuplicateKeyException e) {
        return conflicto(new CorreoYaRegistrado().getMessage());
    }

    private static ResponseEntity<?> conflicto(String mensaje) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", mensaje));
    }

    /**
     * https://dominio-por-el-que-entró: ya fue validado como dominio registrado de ESTE gimnasio (si no, el
     * request no habría llegado acá). En desarrollo el proxy de Angular conserva el Host (changeOrigin: false).
     */
    private static String urlBase(HttpServletRequest request) {
        int puerto = request.getServerPort();
        boolean estandar = ("http".equals(request.getScheme()) && puerto == 80) || ("https".equals(request.getScheme()) && puerto == 443);
        return request.getScheme() + "://" + request.getServerName() + (estandar ? "" : ":" + puerto);
    }
}
