package com.gimnasio.auditoria;

import org.springframework.stereotype.Service;

import com.gimnasio.plataforma.BaseDelGimnasio;

/**
 * Registra acciones en la tabla {@code auditoria} del gimnasio ACTUAL.
 *
 * <p><b>Regla:</b> el detalle nunca lleva datos de salud, contraseñas, tokens ni códigos. Lo que se audita es
 * la acción ("se envió una invitación"), no el secreto.</p>
 */
@Service
public class Auditoria {

    private static final int LARGO_DETALLE = 500;

    private final BaseDelGimnasio base;

    public Auditoria(BaseDelGimnasio base) {
        this.base = base;
    }

    /**
     * @param actor     quién: {@code usuario:12}, {@code sistema}, {@code web}
     * @param accion    qué, en MAYÚSCULAS: {@code INSCRIPCION_CARGADA}
     * @param recurso   sobre qué: {@code inscripcion}
     * @param recursoId id del recurso, o null
     * @param detalle   texto corto opcional, sin datos sensibles
     */
    public void registrar(String actor, String accion, String recurso, Long recursoId, String detalle) {
        String recortado = detalle == null || detalle.length() <= LARGO_DETALLE ? detalle : detalle.substring(0, LARGO_DETALLE);
        base.jdbc().update("INSERT INTO auditoria (actor, accion, recurso, recurso_id, detalle) VALUES (?, ?, ?, ?, ?)",
                actor, accion, recurso, recursoId, recortado);
    }

    /** Actor estándar para un usuario del gimnasio. */
    public static String usuario(long usuarioId) {
        return "usuario:" + usuarioId;
    }
}
