package com.gimnasio.inscripcion;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.gimnasio.auditoria.Auditoria;
import com.gimnasio.identidad.ServicioUsuarios;
import com.gimnasio.notificaciones.Correos;
import com.gimnasio.plataforma.BaseDelGimnasio;
import com.gimnasio.plataforma.ContextoGimnasio;

/**
 * Inscripción de alumnos en el gimnasio ACTUAL (ADR-0007).
 *
 * <p>Todo lo que se guarda va en UNA transacción; el correo sale DESPUÉS de confirmarla (si fallara la base,
 * no queremos haber mandado una invitación a una inscripción que no existe).</p>
 */
@Service
public class ServicioInscripcion {

    private final BaseDelGimnasio base;
    private final ServicioUsuarios usuarios;
    private final Invitaciones invitaciones;
    private final Correos correos;
    private final Auditoria auditoria;
    private final Clock reloj;

    public ServicioInscripcion(BaseDelGimnasio base, ServicioUsuarios usuarios, Invitaciones invitaciones, Correos correos,
            Auditoria auditoria, Clock reloj) {
        this.base = base;
        this.usuarios = usuarios;
        this.invitaciones = invitaciones;
        this.correos = correos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /** Fecha de hoy en la zona horaria DEL GIMNASIO (no la del servidor): define quién es menor. */
    public LocalDate hoy() {
        return LocalDate.now(reloj.withZone(ContextoGimnasio.actual().zonaHoraria()));
    }

    /**
     * Carga una preinscripción en el mostrador. Cuenta como la aprobación inicial (ADR-0007 §1).
     * Con correo: crea la cuenta INVITADA y manda la invitación. Sin correo: la ficha se completa en el mostrador.
     *
     * @param datos     ya validados ({@link DatosMostrador#errores})
     * @param emailActor quién la carga (para la auditoría)
     * @param urlBase   https://dominio-del-gimnasio, para armar el enlace de la invitación
     * @throws CorreoYaRegistrado si el correo ya es de un alumno o de un usuario de este gimnasio
     */
    public InscripcionCargada inscribirEnMostrador(DatosMostrador datos, String emailActor, String urlBase) {
        DatosMostrador d = datos.normalizados();
        Long actorId = usuarios.idDe(emailActor).orElse(null);
        String actor = actorId == null ? "desconocido" : Auditoria.usuario(actorId);

        record Guardado(long inscripcionId, long alumnoId, String token) {
        }
        Guardado guardado = base.enTransaccion(() -> {
            if (d.email() != null && correoEnUso(d.email())) {
                throw new CorreoYaRegistrado();
            }
            Long usuarioId = d.email() == null ? null : usuarios.crearInvitado(d.email(), d.nombre() + " " + d.apellido());
            base.jdbc().update("""
                    INSERT INTO alumno (nombre, apellido, email, celular, fecha_nacimiento, usuario_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, d.nombre(), d.apellido(), d.email(), d.celular(), d.fechaNacimiento(), usuarioId);
            long alumnoId = ultimoId();
            base.jdbc().update("""
                    INSERT INTO inscripcion (tipo, origen, estado, alumno_id, cargada_por)
                    VALUES ('ALUMNO', 'MOSTRADOR', 'FICHA_PENDIENTE', ?, ?)
                    """, alumnoId, actorId);
            long inscripcionId = ultimoId();
            auditoria.registrar(actor, "INSCRIPCION_CARGADA", "inscripcion", inscripcionId,
                    d.email() == null ? "mostrador, sin correo" : "mostrador, con correo");
            String token = null;
            if (d.email() != null) {
                token = invitaciones.nueva(inscripcionId);
                auditoria.registrar(actor, "INVITACION_ENVIADA", "inscripcion", inscripcionId, null);
            }
            return new Guardado(inscripcionId, alumnoId, token);
        });

        if (guardado.token() != null) {
            correos.enviar(Invitaciones.correo(d.email(), d.nombre(), urlBase, guardado.token()));
        }
        return new InscripcionCargada(guardado.inscripcionId(), guardado.alumnoId(), "FICHA_PENDIENTE",
                guardado.token() != null, d.esMenorEn(hoy()));
    }

    /** Resultado de pedir un reenvío de invitación. */
    public enum Reenvio { ENVIADA, NO_ENCONTRADA, SIN_CORREO, NO_CORRESPONDE, MUY_PRONTO }

    /** Reenvía la invitación (anula la anterior). Solo si la ficha sigue pendiente y tiene correo. */
    public Reenvio reenviarInvitacion(long inscripcionId, String emailActor, String urlBase) {
        Optional<Object[]> fila = base.jdbc().query("""
                SELECT i.estado, a.email, a.nombre FROM inscripcion i JOIN alumno a ON a.id = i.alumno_id WHERE i.id = ?
                """, (rs, n) -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3)}, inscripcionId)
                .stream().findFirst();
        if (fila.isEmpty()) {
            return Reenvio.NO_ENCONTRADA;
        }
        String estado = (String) fila.get()[0];
        String email = (String) fila.get()[1];
        if (!"FICHA_PENDIENTE".equals(estado)) {
            return Reenvio.NO_CORRESPONDE;
        }
        if (email == null) {
            return Reenvio.SIN_CORREO;
        }
        if (!invitaciones.puedeReenviar(inscripcionId)) {
            return Reenvio.MUY_PRONTO;
        }
        String actor = usuarios.idDe(emailActor).map(Auditoria::usuario).orElse("desconocido");
        String token = base.enTransaccion(() -> {
            String nuevo = invitaciones.nueva(inscripcionId);
            auditoria.registrar(actor, "INVITACION_REENVIADA", "inscripcion", inscripcionId, null);
            return nuevo;
        });
        correos.enviar(Invitaciones.correo(email, (String) fila.get()[2], urlBase, token));
        return Reenvio.ENVIADA;
    }

    /** Las últimas 200 inscripciones del gimnasio actual, opcionalmente filtradas por estado. */
    public List<ResumenInscripcion> listar(String estado) {
        String sql = """
                SELECT i.id, a.nombre, a.apellido, a.email, a.celular, i.estado, i.origen, i.creada_en
                FROM inscripcion i JOIN alumno a ON a.id = i.alumno_id
                """ + (estado == null ? "" : " WHERE i.estado = ?") + " ORDER BY i.creada_en DESC, i.id DESC LIMIT 200";
        Object[] parametros = estado == null ? new Object[0] : new Object[] {estado};
        return base.jdbc().query(sql, (rs, n) -> new ResumenInscripcion(rs.getLong("id"), rs.getString("nombre"),
                rs.getString("apellido"), rs.getString("email"), rs.getString("celular"), rs.getString("estado"),
                rs.getString("origen"), rs.getObject("creada_en", Timestamp.class).toInstant()), parametros);
    }

    private boolean correoEnUso(String email) {
        Boolean enUso = base.jdbc().queryForObject(
                "SELECT EXISTS (SELECT 1 FROM alumno WHERE email = ?) OR EXISTS (SELECT 1 FROM usuario WHERE email = ?)",
                Boolean.class, email, email);
        return Boolean.TRUE.equals(enUso);
    }

    /** LAST_INSERT_ID() es por conexión: dentro de la transacción es seguro (misma conexión). */
    private long ultimoId() {
        Long id = base.jdbc().queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0 : id;
    }
}
