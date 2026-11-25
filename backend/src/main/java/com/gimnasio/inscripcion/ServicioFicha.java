package com.gimnasio.inscripcion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.gimnasio.auditoria.Auditoria;
import com.gimnasio.identidad.ServicioUsuarios;
import com.gimnasio.plataforma.BaseDelGimnasio;
import com.gimnasio.plataforma.ContextoGimnasio;

/**
 * Completar la ficha del alumno, por invitación o en el mostrador (ADR-0007, UC-05 y UC-06).
 *
 * <p>Al enviarla, según el modo del gimnasio: {@code SIMPLE} → alumno y cuenta ACTIVOS con el rol ALUMNO;
 * {@code COMPLETO} → EN_REVISION, hasta que el personal la apruebe. El solicitante nunca elige su rol (INS-05).</p>
 */
@Service
public class ServicioFicha {

    /** Por dónde se completó la ficha (queda en la aceptación de los documentos). */
    public enum Medio { INVITACION, MOSTRADOR }

    private final BaseDelGimnasio base;
    private final ServicioUsuarios usuarios;
    private final Auditoria auditoria;
    private final Clock reloj;

    public ServicioFicha(BaseDelGimnasio base, ServicioUsuarios usuarios, Auditoria auditoria, Clock reloj) {
        this.base = base;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * La inscripción de una invitación válida: el token coincide, no se usó, no venció y la ficha sigue
     * pendiente. La invitación NO se consume al abrirla (se puede cerrar y volver): se consume al enviar.
     */
    public Optional<Long> inscripcionDeInvitacion(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }
        return base.jdbc().queryForList("""
                SELECT i.id FROM invitacion inv JOIN inscripcion i ON i.id = inv.inscripcion_id
                WHERE inv.hash_token = ? AND inv.usada_ms IS NULL AND inv.expira_ms > ? AND i.estado = 'FICHA_PENDIENTE'
                """, Long.class, Invitaciones.hash(token), reloj.millis()).stream().findFirst();
    }

    /** La ficha de una inscripción del gimnasio actual, con los documentos que le corresponde aceptar. */
    public Optional<FichaAlumno> leer(long inscripcionId) {
        return base.jdbc().query("""
                SELECT i.id, i.estado, a.nombre, a.apellido, a.email, a.fecha_nacimiento, a.dni, a.direccion,
                       a.emergencia_nombre, a.emergencia_telefono, a.objetivo, a.responsable_nombre, a.responsable_dni,
                       a.responsable_telefono, a.responsable_email, a.responsable_vinculo
                FROM inscripcion i JOIN alumno a ON a.id = i.alumno_id WHERE i.id = ?
                """, (rs, n) -> armarFicha(rs), inscripcionId).stream().findFirst();
    }

    /** Guarda lo que haya (borrador). Solo mientras la ficha está pendiente. */
    public void guardarBorrador(long inscripcionId, DatosFicha datos) {
        DatosFicha d = datos.normalizados();
        int filas = base.jdbc().update("""
                UPDATE alumno a JOIN inscripcion i ON i.alumno_id = a.id
                SET a.dni = ?, a.direccion = ?, a.emergencia_nombre = ?, a.emergencia_telefono = ?, a.objetivo = ?,
                    a.responsable_nombre = ?, a.responsable_dni = ?, a.responsable_telefono = ?, a.responsable_email = ?,
                    a.responsable_vinculo = ?
                WHERE i.id = ? AND i.estado = 'FICHA_PENDIENTE'
                """, d.dni(), d.direccion(), d.emergenciaNombre(), d.emergenciaTelefono(), d.objetivo(), d.responsableNombre(),
                d.responsableDni(), d.responsableTelefono(), d.responsableEmail(), d.responsableVinculo(), inscripcionId);
        if (filas == 0) {
            throw new FichaNoEditable();
        }
    }

    /**
     * Guarda los datos y, si están completos y se aceptaron todos los documentos vigentes, envía la ficha.
     *
     * @param actor         para la auditoría: {@code usuario:12} (mostrador) o {@code invitacion}
     * @param registradoPor usuario del personal si fue en el mostrador; null por invitación
     * @param ip            desde dónde se aceptó (solo por invitación)
     */
    public ResultadoEnvio enviar(long inscripcionId, DatosFicha datos, List<DocumentoAceptado> aceptados, Medio medio,
            String actor, Long registradoPor, String ip) {
        FichaAlumno ficha = leer(inscripcionId).filter(f -> "FICHA_PENDIENTE".equals(f.estado()))
                .orElseThrow(FichaNoEditable::new);
        DatosFicha d = datos.normalizados();
        List<String> errores = new java.util.ArrayList<>(d.erroresParaEnviar(ficha.esMenor()));
        Set<DocumentoAceptado> pedidos = ficha.documentos().stream()
                .map(doc -> new DocumentoAceptado(doc.codigo(), doc.version())).collect(Collectors.toSet());
        if (!new HashSet<>(aceptados == null ? List.of() : aceptados).containsAll(pedidos)) {
            errores.add("documentos: hay que aceptar todos los documentos vigentes (si cambiaron, volvé a leerlos)");
        }
        if (!errores.isEmpty()) {
            return ResultadoEnvio.conErrores(errores);
        }

        String modo = modo();
        String estadoFinal = "COMPLETO".equals(modo) ? "EN_REVISION" : "ACTIVO";
        String aceptadoPor = ficha.esMenor() ? d.responsableNombre() : ficha.nombre() + " " + ficha.apellido();
        base.enTransaccion(() -> {
            guardarBorrador(inscripcionId, d);
            long alumnoId = alumnoDe(inscripcionId);
            for (DocumentoLegal documento : ficha.documentos()) {
                base.jdbc().update("""
                        INSERT INTO aceptacion_documento (alumno_id, documento_id, medio, aceptado_por, registrado_por, ip)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, alumnoId, documento.id(), medio.name(), aceptadoPor, registradoPor, ip);
            }
            base.jdbc().update("UPDATE invitacion SET usada_ms = ? WHERE inscripcion_id = ? AND usada_ms IS NULL",
                    reloj.millis(), inscripcionId);
            base.jdbc().update("UPDATE alumno SET estado = ? WHERE id = ?",
                    "ACTIVO".equals(estadoFinal) ? "ACTIVO" : "EN_REVISION", alumnoId);
            base.jdbc().update("UPDATE inscripcion SET estado = ? WHERE id = ?",
                    "ACTIVO".equals(estadoFinal) ? "APROBADA" : "EN_REVISION", inscripcionId);
            auditoria.registrar(actor, "FICHA_ENVIADA", "inscripcion", inscripcionId, "medio " + medio.name());

            Long usuarioId = base.jdbc().queryForObject("SELECT usuario_id FROM alumno WHERE id = ?", Long.class, alumnoId);
            if (usuarioId != null) {
                usuarios.avanzarInscripcion(usuarioId, estadoFinal);
                if ("ACTIVO".equals(estadoFinal)) {
                    // El rol lo asigna el sistema, nunca el solicitante (INS-05).
                    usuarios.asignarRol(usuarios.emailDe(usuarioId).orElseThrow(), "ALUMNO");
                }
            }
            if ("ACTIVO".equals(estadoFinal)) {
                auditoria.registrar("sistema", "ALUMNO_ACTIVADO", "alumno", alumnoId, "modo SIMPLE");
            }
            return null;
        });
        return new ResultadoEnvio(estadoFinal, List.of());
    }

    /** Modo de aprobación del gimnasio (ADR-0007 §1). Ante un valor desconocido, el más estricto. */
    public String modo() {
        String valor = base.jdbc().queryForList("SELECT valor FROM configuracion_gimnasio WHERE clave = 'inscripcion.modo'",
                String.class).stream().findFirst().orElse("SIMPLE");
        return "SIMPLE".equals(valor) ? "SIMPLE" : "COMPLETO";
    }

    private FichaAlumno armarFicha(ResultSet rs) throws SQLException {
        LocalDate nacimiento = rs.getObject("fecha_nacimiento", LocalDate.class);
        LocalDate hoy = LocalDate.now(reloj.withZone(ContextoGimnasio.actual().zonaHoraria()));
        boolean esMenor = nacimiento.plusYears(DatosMostrador.MAYORIA_DE_EDAD).isAfter(hoy);
        DatosFicha datos = new DatosFicha(rs.getString("dni"), rs.getString("direccion"), rs.getString("emergencia_nombre"),
                rs.getString("emergencia_telefono"), rs.getString("objetivo"), rs.getString("responsable_nombre"),
                rs.getString("responsable_dni"), rs.getString("responsable_telefono"), rs.getString("responsable_email"),
                rs.getString("responsable_vinculo"));
        return new FichaAlumno(rs.getLong("id"), rs.getString("nombre"), rs.getString("apellido"), rs.getString("email"),
                nacimiento, esMenor, rs.getString("estado"), datos, documentosVigentes(esMenor));
    }

    private List<DocumentoLegal> documentosVigentes(boolean esMenor) {
        return base.jdbc().query("""
                SELECT id, codigo, version, titulo, texto, solo_menores FROM documento_legal
                WHERE vigente AND (? OR NOT solo_menores) ORDER BY id
                """, (rs, n) -> new DocumentoLegal(rs.getLong("id"), rs.getString("codigo"), rs.getInt("version"),
                rs.getString("titulo"), rs.getString("texto"), rs.getBoolean("solo_menores")), esMenor);
    }

    private long alumnoDe(long inscripcionId) {
        Long id = base.jdbc().queryForObject("SELECT alumno_id FROM inscripcion WHERE id = ?", Long.class, inscripcionId);
        if (id == null) {
            throw new FichaNoEditable();
        }
        return id;
    }
}
