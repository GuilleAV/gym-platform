package com.gimnasio.identidad.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.gimnasio.plataforma.BaseDelGimnasio;

/**
 * Límite de intentos de inicio de sesión, por <b>cuenta</b> y por <b>IP</b> (paso 0.6b).
 *
 * <ul>
 *   <li><b>Por cuenta:</b> frena a quien prueba muchas claves contra UN correo.</li>
 *   <li><b>Por IP:</b> frena a quien prueba UNA clave común ("123456") contra muchos correos
 *       (<i>password spraying</i>), que el límite por cuenta no detecta.</li>
 * </ul>
 *
 * <p>Los contadores viven en la base del gimnasio ACTUAL (tabla {@code intento_ingreso}), igual que los
 * usuarios: un ataque contra un gimnasio no bloquea a nadie de otro. Y al estar en la base y no en
 * memoria, sobreviven a un reinicio y funcionan igual si un día hay dos instancias de la app.</p>
 *
 * <p><b>Ojo detrás de un proxy:</b> la IP sale de {@code request.getRemoteAddr()}. Si la app queda detrás
 * de un proxy inverso (Nginx, Cloudflare), todas las IPs serían la del proxy: hay que configurar
 * {@code server.forward-headers-strategy} y que el proxy sea el único que pueda poner X-Forwarded-For.
 * Nunca leer ese header a mano: el cliente lo puede inventar.</p>
 */
@Component
public class LimitadorDeIntentos {

    static final String CUENTA = "CUENTA";
    static final String IP = "IP";

    /** Los registros sin actividad por más de un día ya no sirven: se borran de a poco. */
    private static final Duration RETENCION = Duration.ofDays(1);
    private static final int BORRADO_POR_VEZ = 100;

    private final BaseDelGimnasio base;
    private final Clock reloj;
    private final PoliticaDeBloqueo porCuenta;
    private final PoliticaDeBloqueo porIp;
    private final Duration ventana;

    LimitadorDeIntentos(BaseDelGimnasio base, Clock reloj, PropiedadesSeguridad propiedades) {
        PropiedadesSeguridad.Ingreso ingreso = propiedades.ingreso();
        this.base = base;
        this.reloj = reloj;
        this.porCuenta = new PoliticaDeBloqueo(ingreso.fallosPorCuenta(), ingreso.bloqueoInicial(), ingreso.bloqueoMaximo());
        this.porIp = new PoliticaDeBloqueo(ingreso.fallosPorIp(), ingreso.bloqueoInicial(), ingreso.bloqueoMaximo());
        this.ventana = ingreso.ventana();
    }

    /** Si el correo o la IP están bloqueados, cuánto falta para que se libere (el mayor de los dos). */
    public Optional<Duration> bloqueoVigente(String email, String ip) {
        long ahora = reloj.millis();
        List<Long> hasta = base.jdbc().queryForList("""
                SELECT bloqueado_hasta_ms FROM intento_ingreso
                WHERE (tipo = ? AND clave_hash = ?) OR (tipo = ? AND clave_hash = ?)
                """, Long.class, CUENTA, hash(email), IP, hash(ip));
        long maximo = hasta.stream().mapToLong(Long::longValue).max().orElse(0);
        return maximo > ahora ? Optional.of(Duration.ofMillis(maximo - ahora)) : Optional.empty();
    }

    /** Un ingreso fallido: suma un fallo a la cuenta y a la IP y, si corresponde, las bloquea. */
    public void registrarFallo(String email, String ip) {
        JdbcTemplate jdbc = base.jdbc();
        long ahora = reloj.millis();
        sumarFallo(jdbc, CUENTA, hash(email), porCuenta, ahora);
        sumarFallo(jdbc, IP, hash(ip), porIp, ahora);
        // Limpieza de a poco, aprovechando el fallo: sin un job aparte que recorra todos los gimnasios.
        jdbc.update("DELETE FROM intento_ingreso WHERE ultimo_fallo_ms < ? AND bloqueado_hasta_ms < ? LIMIT " + BORRADO_POR_VEZ,
                ahora - RETENCION.toMillis(), ahora);
    }

    /**
     * Un ingreso correcto pone en cero el contador de la CUENTA. El de la IP no: si no, un atacante con
     * una cuenta propia podría "limpiar" su IP ingresando bien entre intento e intento.
     */
    public void registrarExito(String email) {
        base.jdbc().update("DELETE FROM intento_ingreso WHERE tipo = ? AND clave_hash = ?", CUENTA, hash(email));
    }

    private void sumarFallo(JdbcTemplate jdbc, String tipo, String claveHash, PoliticaDeBloqueo politica, long ahora) {
        // Un solo INSERT ... ON DUPLICATE KEY UPDATE: es atómico, así dos fallos simultáneos no pierden
        // la cuenta. Si el último fallo es más viejo que la ventana, el contador arranca de nuevo en 1.
        jdbc.update("""
                INSERT INTO intento_ingreso (tipo, clave_hash, fallos, ultimo_fallo_ms) VALUES (?, ?, 1, ?) AS nuevo
                ON DUPLICATE KEY UPDATE
                    fallos = IF(intento_ingreso.ultimo_fallo_ms < ?, 1, intento_ingreso.fallos + 1),
                    ultimo_fallo_ms = nuevo.ultimo_fallo_ms
                """, tipo, claveHash, ahora, ahora - ventana.toMillis());
        Integer fallos = jdbc.queryForObject("SELECT fallos FROM intento_ingreso WHERE tipo = ? AND clave_hash = ?",
                Integer.class, tipo, claveHash);
        Duration bloqueo = politica.bloqueoPara(fallos == null ? 0 : fallos);
        if (!bloqueo.isZero()) {
            jdbc.update("UPDATE intento_ingreso SET bloqueado_hasta_ms = ? WHERE tipo = ? AND clave_hash = ?",
                    ahora + bloqueo.toMillis(), tipo, claveHash);
        }
    }

    /** SHA-256 en hexadecimal del valor en minúsculas (el correo ya llega normalizado). */
    static String hash(String valor) {
        String normalizado = valor == null || valor.isBlank() ? "desconocido" : valor.strip().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalizado.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e); // imposible: es obligatorio en toda JVM
        }
    }
}
