package com.gimnasio.identidad.mfa;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.gimnasio.identidad.ServicioUsuarios;
import com.gimnasio.plataforma.BaseDelGimnasio;
import com.gimnasio.plataforma.CifradoDeDatos;
import com.gimnasio.plataforma.ContextoGimnasio;

/**
 * Segundo factor (TOTP + códigos de recuperación) de los usuarios del gimnasio ACTUAL (ADR-0005).
 *
 * <p>Como el resto del módulo, nunca recibe un id de gimnasio: trabaja sobre la base del contexto.</p>
 */
@Service
public class ServicioMfa {

    static final int CANTIDAD_CODIGOS_RECUPERACION = 10;
    /** Sin I, O, 0 ni 1: no se confunden al leerlos de un papel. 32 símbolos = 5 bits por carácter. */
    private static final String ALFABETO_RECUPERACION = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** 10 caracteres × 5 bits = 50 bits por código. */
    private static final int LARGO_CODIGO_RECUPERACION = 10;

    private final BaseDelGimnasio base;
    private final CifradoDeDatos cifrado;
    private final PasswordEncoder codificador;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    public ServicioMfa(BaseDelGimnasio base, CifradoDeDatos cifrado, PasswordEncoder codificador, Clock reloj) {
        this.base = base;
        this.cifrado = cifrado;
        this.codificador = codificador;
        this.reloj = reloj;
    }

    /** ¿Qué le falta a este usuario después de la contraseña? (algún rol activo con mfa = sí → MFA) */
    public RequisitoMfa requisitoPara(String email) {
        String correo = ServicioUsuarios.normalizarEmail(email);
        Boolean exige = base.jdbc().queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM usuario u
                    JOIN usuario_rol ur ON ur.usuario_id = u.id
                    JOIN rol r ON r.id = ur.rol_id
                    WHERE u.email = ? AND r.activo AND r.mfa)
                """, Boolean.class, correo);
        if (!Boolean.TRUE.equals(exige)) {
            return RequisitoMfa.NO_REQUERIDO;
        }
        Boolean configurado = base.jdbc().queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM usuario_mfa m JOIN usuario u ON u.id = m.usuario_id
                    WHERE u.email = ? AND m.confirmado_en IS NOT NULL)
                """, Boolean.class, correo);
        return Boolean.TRUE.equals(configurado) ? RequisitoMfa.CODIGO : RequisitoMfa.CONFIGURAR;
    }

    /**
     * Genera un secreto nuevo (todavía sin confirmar) y devuelve los datos para el QR. Si había uno sin
     * confirmar, lo reemplaza (ej. cerró la pantalla y volvió a empezar). Si ya está confirmado, falla:
     * cambiar el celular requiere otro flujo, no se puede pisar en silencio.
     */
    public ConfiguracionMfa iniciarConfiguracion(String email) {
        long usuarioId = idDe(email);
        Boolean confirmado = base.jdbc().queryForObject(
                "SELECT EXISTS (SELECT 1 FROM usuario_mfa WHERE usuario_id = ? AND confirmado_en IS NOT NULL)",
                Boolean.class, usuarioId);
        if (Boolean.TRUE.equals(confirmado)) {
            throw new IllegalStateException("El usuario ya tiene el MFA configurado");
        }
        byte[] secreto = Totp.nuevoSecreto();
        String secretoBase32 = Totp.base32(secreto);
        // El IF protege también si dos requests llegan a la vez: un secreto confirmado nunca se pisa.
        base.jdbc().update("""
                INSERT INTO usuario_mfa (usuario_id, secreto_cifrado) VALUES (?, ?) AS nuevo
                ON DUPLICATE KEY UPDATE
                    secreto_cifrado = IF(usuario_mfa.confirmado_en IS NULL, nuevo.secreto_cifrado, usuario_mfa.secreto_cifrado),
                    ultimo_paso = IF(usuario_mfa.confirmado_en IS NULL, 0, usuario_mfa.ultimo_paso)
                """, usuarioId, cifrado.cifrar(secretoBase32));
        String emisor = ContextoGimnasio.actual().nombre();
        return new ConfiguracionMfa(secretoBase32, Totp.uri(emisor, ServicioUsuarios.normalizarEmail(email), secreto));
    }

    /**
     * Confirma la configuración con el primer código de la app. Si es correcto, activa el MFA y devuelve
     * los códigos de recuperación EN CLARO (es la única vez que existen así: en la base quedan con Argon2id).
     */
    public Optional<List<String>> confirmarConfiguracion(String email, String codigo) {
        long usuarioId = idDe(email);
        List<byte[]> pendiente = base.jdbc().queryForList(
                "SELECT secreto_cifrado FROM usuario_mfa WHERE usuario_id = ? AND confirmado_en IS NULL", byte[].class, usuarioId);
        if (pendiente.isEmpty()) {
            return Optional.empty();
        }
        long paso = Totp.verificar(secretoDe(pendiente.getFirst()), codigo, reloj.millis());
        if (paso < 0) {
            return Optional.empty();
        }
        JdbcTemplate jdbc = base.jdbc();
        int filas = jdbc.update("""
                UPDATE usuario_mfa SET confirmado_en = CURRENT_TIMESTAMP(6), ultimo_paso = ?
                WHERE usuario_id = ? AND confirmado_en IS NULL
                """, paso, usuarioId);
        if (filas == 0) {
            return Optional.empty(); // otro request lo confirmó al mismo tiempo
        }
        return Optional.of(regenerarCodigosDeRecuperacion(jdbc, usuarioId));
    }

    /**
     * Verifica un código de la app (6 dígitos) o un código de recuperación. Cada código sirve una sola vez.
     */
    public boolean verificar(String email, String codigo) {
        if (codigo == null || codigo.isBlank() || codigo.length() > 32) {
            return false;
        }
        long usuarioId = idDe(email);
        String limpio = codigo.strip();
        return limpio.matches("\\d{" + Totp.DIGITOS + "}")
                ? verificarTotp(usuarioId, limpio)
                : usarCodigoDeRecuperacion(usuarioId, limpio);
    }

    /** Cuántos códigos de recuperación sin usar le quedan (para avisarle cuando se le terminan). */
    public int codigosDeRecuperacionRestantes(String email) {
        Integer restantes = base.jdbc().queryForObject(
                "SELECT COUNT(*) FROM codigo_recuperacion WHERE usuario_id = ? AND usado_en IS NULL", Integer.class, idDe(email));
        return restantes == null ? 0 : restantes;
    }

    private boolean verificarTotp(long usuarioId, String codigo) {
        List<byte[]> confirmado = base.jdbc().queryForList(
                "SELECT secreto_cifrado FROM usuario_mfa WHERE usuario_id = ? AND confirmado_en IS NOT NULL", byte[].class, usuarioId);
        if (confirmado.isEmpty()) {
            return false;
        }
        long paso = Totp.verificar(secretoDe(confirmado.getFirst()), codigo, reloj.millis());
        if (paso < 0) {
            return false;
        }
        // Anti-reuso, atómico: solo se acepta si el paso es POSTERIOR al último usado. Si alguien ve el
        // código por encima del hombro y lo escribe en los mismos 30 segundos, ya no sirve.
        return base.jdbc().update("UPDATE usuario_mfa SET ultimo_paso = ? WHERE usuario_id = ? AND ultimo_paso < ?",
                paso, usuarioId, paso) == 1;
    }

    private boolean usarCodigoDeRecuperacion(long usuarioId, String codigo) {
        String normalizado = codigo.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (normalizado.length() != LARGO_CODIGO_RECUPERACION) {
            return false;
        }
        JdbcTemplate jdbc = base.jdbc();
        List<Object[]> disponibles = jdbc.query(
                "SELECT id, hash_codigo FROM codigo_recuperacion WHERE usuario_id = ? AND usado_en IS NULL",
                (fila, n) -> new Object[] {fila.getLong("id"), fila.getString("hash_codigo")}, usuarioId);
        for (Object[] disponible : disponibles) {
            if (codificador.matches(normalizado, (String) disponible[1])) {
                // "AND usado_en IS NULL": si dos requests usan el mismo código a la vez, solo uno gana.
                return jdbc.update("UPDATE codigo_recuperacion SET usado_en = CURRENT_TIMESTAMP(6) WHERE id = ? AND usado_en IS NULL",
                        disponible[0]) == 1;
            }
        }
        return false;
    }

    private List<String> regenerarCodigosDeRecuperacion(JdbcTemplate jdbc, long usuarioId) {
        jdbc.update("DELETE FROM codigo_recuperacion WHERE usuario_id = ?", usuarioId);
        List<String> codigos = new ArrayList<>(CANTIDAD_CODIGOS_RECUPERACION);
        for (int i = 0; i < CANTIDAD_CODIGOS_RECUPERACION; i++) {
            String codigo = codigoAleatorio();
            jdbc.update("INSERT INTO codigo_recuperacion (usuario_id, hash_codigo) VALUES (?, ?)", usuarioId,
                    codificador.encode(codigo));
            codigos.add(codigo.substring(0, 5) + "-" + codigo.substring(5)); // "ABCDE-FGHJK": más fácil de copiar
        }
        return List.copyOf(codigos);
    }

    private String codigoAleatorio() {
        StringBuilder codigo = new StringBuilder(LARGO_CODIGO_RECUPERACION);
        for (int i = 0; i < LARGO_CODIGO_RECUPERACION; i++) {
            codigo.append(ALFABETO_RECUPERACION.charAt(azar.nextInt(ALFABETO_RECUPERACION.length())));
        }
        return codigo.toString();
    }

    private byte[] secretoDe(byte[] secretoCifrado) {
        return Totp.desdeBase32(cifrado.descifrar(secretoCifrado));
    }

    private long idDe(String email) {
        List<Long> ids = base.jdbc().queryForList("SELECT id FROM usuario WHERE email = ?", Long.class,
                ServicioUsuarios.normalizarEmail(email));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Usuario inexistente");
        }
        return ids.getFirst();
    }
}
