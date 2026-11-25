package com.gimnasio.identidad;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.gimnasio.plataforma.BaseDelGimnasio;

/**
 * Usuarios del gimnasio ACTUAL (siempre el del contexto: nunca recibe un id de gimnasio).
 */
@Service
public class ServicioUsuarios {

    private final BaseDelGimnasio base;
    private final PasswordEncoder codificador;

    public ServicioUsuarios(BaseDelGimnasio base, PasswordEncoder codificador) {
        this.base = base;
        this.codificador = codificador;
    }

    /** Correos en minúsculas y sin espacios: "Ana@Mail.com " y "ana@mail.com" son la misma cuenta. */
    public static String normalizarEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Crea un usuario ACTIVO si no existe (idempotente). Uso actual: usuarios de demostración.
     * El alta real será por invitación con enlace de un solo uso (INS-04, paso siguiente).
     */
    public void crearActivoSiNoExiste(String email, String nombre, String clave) {
        if (!Contrasenias.largoValido(clave)) {
            throw new IllegalArgumentException("La contraseña debe tener entre " + Contrasenias.LARGO_MINIMO
                    + " y " + Contrasenias.LARGO_MAXIMO + " caracteres");
        }
        base.jdbc().update("INSERT IGNORE INTO usuario (email, nombre, hash_clave, estado) VALUES (?, ?, ?, 'ACTIVO')",
                normalizarEmail(email), nombre, codificador.encode(clave));
    }

    /**
     * Le asigna un rol al usuario (idempotente: si ya lo tenía, no hace nada). Un usuario puede tener
     * varios roles (ADR-0005).
     */
    public void asignarRol(String email, String codigoRol) {
        int encontrados = base.jdbc().update("""
                INSERT IGNORE INTO usuario_rol (usuario_id, rol_id)
                SELECT u.id, r.id FROM usuario u JOIN rol r ON r.codigo = ?
                WHERE u.email = ?
                """, codigoRol, normalizarEmail(email));
        if (encontrados == 0 && !rolesDe(email).contains(codigoRol)) {
            throw new IllegalArgumentException("Usuario o rol inexistente");
        }
    }

    /** Códigos de los roles del usuario (activos o no). */
    public java.util.List<String> rolesDe(String email) {
        return base.jdbc().queryForList("""
                SELECT r.codigo FROM rol r JOIN usuario_rol ur ON ur.rol_id = r.id JOIN usuario u ON u.id = ur.usuario_id
                WHERE u.email = ? ORDER BY r.codigo
                """, String.class, normalizarEmail(email));
    }

    /**
     * Crea una cuenta INVITADA (sin contraseña: entra con código, ADR-0006) para quien se está inscribiendo.
     * Se activa al terminar la ficha (ADR-0007).
     *
     * @return el id del usuario creado
     * @throws IllegalStateException si el correo ya tiene cuenta en este gimnasio
     */
    public long crearInvitado(String email, String nombre) {
        String correo = normalizarEmail(email);
        if (idDe(correo).isPresent()) {
            throw new IllegalStateException("El correo ya tiene cuenta en este gimnasio");
        }
        base.jdbc().update("INSERT INTO usuario (email, nombre, estado) VALUES (?, ?, 'INVITADO')", correo, nombre);
        return idDe(correo).orElseThrow();
    }

    /** Id del usuario con ese correo en el gimnasio actual. */
    public Optional<Long> idDe(String email) {
        return base.jdbc().queryForList("SELECT id FROM usuario WHERE email = ?", Long.class, normalizarEmail(email))
                .stream().findFirst();
    }

    /**
     * Pasa una cuenta INVITADA (o EN_REVISION) al estado indicado: ACTIVO o EN_REVISION (ADR-0007).
     * Nunca toca cuentas ya activas o suspendidas: activar a alguien suspendido es otra decisión.
     *
     * @return false si no había una cuenta en un estado que se pueda cambiar
     */
    public boolean avanzarInscripcion(long usuarioId, String nuevoEstado) {
        if (!"ACTIVO".equals(nuevoEstado) && !"EN_REVISION".equals(nuevoEstado)) {
            throw new IllegalArgumentException("Estado no permitido: " + nuevoEstado);
        }
        return base.jdbc().update("UPDATE usuario SET estado = ? WHERE id = ? AND estado IN ('INVITADO', 'EN_REVISION')",
                nuevoEstado, usuarioId) == 1;
    }

    /** Correo del usuario (para asignarle roles por correo). */
    public Optional<String> emailDe(long usuarioId) {
        return base.jdbc().queryForList("SELECT email FROM usuario WHERE id = ?", String.class, usuarioId).stream().findFirst();
    }

    public Optional<String> nombreDe(String email) {
        return base.jdbc().queryForList("SELECT nombre FROM usuario WHERE email = ?", String.class, normalizarEmail(email))
                .stream().findFirst();
    }
}
