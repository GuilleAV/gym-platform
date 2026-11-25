package com.gimnasio.identidad.codigo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.gimnasio.identidad.ServicioUsuarios;
import com.gimnasio.identidad.seguridad.PropiedadesSeguridad;
import com.gimnasio.notificaciones.Correo;
import com.gimnasio.notificaciones.Correos;
import com.gimnasio.plataforma.BaseDelGimnasio;
import com.gimnasio.plataforma.ContextoGimnasio;

/**
 * Ingreso con código por correo, en el gimnasio ACTUAL (ADR-0006 §3).
 *
 * <p><b>Regla de oro:</b> {@link #solicitar} nunca le dice a quien llama si el correo existe. Si no
 * existe, está suspendido o ya se le mandaron demasiados, simplemente no envía nada.</p>
 */
@Service
public class ServicioCodigoDeIngreso {

    private static final long UNA_HORA_MS = 3_600_000L;

    private final BaseDelGimnasio base;
    private final Correos correos;
    private final Clock reloj;
    private final PropiedadesSeguridad.CodigoCorreo reglas;
    private final SecureRandom azar = new SecureRandom();

    public ServicioCodigoDeIngreso(BaseDelGimnasio base, Correos correos, Clock reloj, PropiedadesSeguridad propiedades) {
        this.base = base;
        this.correos = correos;
        this.reloj = reloj;
        this.reglas = propiedades.codigoCorreo();
    }

    /** Si el usuario existe, está activo y no superó los límites de envío, le manda un código nuevo. */
    public void solicitar(String email) {
        String correo = ServicioUsuarios.normalizarEmail(email);
        JdbcTemplate jdbc = base.jdbc();
        List<Long> ids = jdbc.queryForList("SELECT id FROM usuario WHERE email = ? AND estado = 'ACTIVO'", Long.class, correo);
        if (ids.isEmpty()) {
            return;
        }
        long usuarioId = ids.getFirst();
        long ahora = reloj.millis();

        Map<String, Object> envios = jdbc.queryForMap("""
                SELECT COUNT(*) AS ultima_hora, COALESCE(MAX(creado_ms), 0) AS ultimo
                FROM codigo_ingreso WHERE usuario_id = ? AND creado_ms > ?
                """, usuarioId, ahora - UNA_HORA_MS);
        long ultimaHora = ((Number) envios.get("ultima_hora")).longValue();
        long ultimo = ((Number) envios.get("ultimo")).longValue();
        if (ultimaHora >= reglas.enviosPorHora() || ahora - ultimo < reglas.esperaEntreEnvios().toMillis()) {
            return;
        }

        // Solo vale el último código: los anteriores sin usar se anulan.
        jdbc.update("UPDATE codigo_ingreso SET usado_ms = ? WHERE usuario_id = ? AND usado_ms IS NULL", ahora, usuarioId);
        String codigo = String.format(Locale.ROOT, "%06d", azar.nextInt(1_000_000));
        jdbc.update("INSERT INTO codigo_ingreso (usuario_id, hash_codigo, creado_ms, expira_ms) VALUES (?, ?, ?, ?)",
                usuarioId, hash(usuarioId, codigo), ahora, ahora + reglas.vigencia().toMillis());

        correos.enviar(armarCorreo(correo, codigo));
    }

    /** ¿Es el código vigente de este usuario? Si es correcto, queda usado (no sirve dos veces). */
    public boolean verificar(String email, String codigo) {
        if (codigo == null || !codigo.strip().matches("\\d{6}")) {
            return false;
        }
        JdbcTemplate jdbc = base.jdbc();
        long ahora = reloj.millis();
        List<Map<String, Object>> vigentes = jdbc.queryForList("""
                SELECT c.id, c.usuario_id, c.hash_codigo FROM codigo_ingreso c JOIN usuario u ON u.id = c.usuario_id
                WHERE u.email = ? AND u.estado = 'ACTIVO' AND c.usado_ms IS NULL AND c.expira_ms > ?
                ORDER BY c.creado_ms DESC LIMIT 1
                """, ServicioUsuarios.normalizarEmail(email), ahora);
        if (vigentes.isEmpty()) {
            return false;
        }
        long id = ((Number) vigentes.getFirst().get("id")).longValue();
        long usuarioId = ((Number) vigentes.getFirst().get("usuario_id")).longValue();
        String guardado = (String) vigentes.getFirst().get("hash_codigo");

        // Se cuenta el intento ANTES de comparar, de forma atómica: con 5 intentos por código, adivinar
        // uno de 1.000.000 tiene una probabilidad de 0,0005 %.
        int contado = jdbc.update("UPDATE codigo_ingreso SET intentos = intentos + 1 WHERE id = ? AND intentos < ?",
                id, reglas.intentosPorCodigo());
        if (contado == 0) {
            jdbc.update("UPDATE codigo_ingreso SET usado_ms = ? WHERE id = ? AND usado_ms IS NULL", ahora, id);
            return false;
        }
        boolean coincide = MessageDigest.isEqual(guardado.getBytes(StandardCharsets.US_ASCII),
                hash(usuarioId, codigo.strip()).getBytes(StandardCharsets.US_ASCII));
        return coincide && jdbc.update("UPDATE codigo_ingreso SET usado_ms = ? WHERE id = ? AND usado_ms IS NULL", ahora, id) == 1;
    }

    private Correo armarCorreo(String para, String codigo) {
        String gimnasio = ContextoGimnasio.actual().nombre();
        long minutos = reglas.vigencia().toMinutes();
        String texto = """
                Hola,

                Tu código para ingresar a %s es:

                    %s

                Vale por %d minutos y sirve una sola vez.

                Si no fuiste vos, ignorá este correo: nadie puede entrar sin el código.
                Nunca te vamos a pedir este código por teléfono ni por WhatsApp.
                """.formatted(gimnasio, codigo, minutos);
        // El código va en el asunto: en el celular se ve en la notificación sin abrir el correo.
        return new Correo(para, codigo + " es tu código para ingresar a " + gimnasio, texto);
    }

    /** Con el id del usuario adentro: el mismo código de dos usuarios da hashes distintos. */
    private static String hash(long usuarioId, String codigo) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((usuarioId + ":" + codigo).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }
}
