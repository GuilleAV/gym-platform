package com.gimnasio.plataforma.aprovisionamiento;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.gimnasio.plataforma.conexiones.CatalogoConexiones;
import com.gimnasio.plataforma.conexiones.DatosConexion;
import com.gimnasio.plataforma.conexiones.PoolsPorGimnasio;
import com.gimnasio.plataforma.conexiones.PropiedadesBd;
import com.gimnasio.plataforma.resolucion.NormalizadorHost;
import com.gimnasio.plataforma.secretos.ResolutorSecretos;

/**
 * Alta automática de un gimnasio (PLA-03): catálogo + base + usuario MySQL + esquema.
 *
 * <p><b>Idempotente:</b> se puede correr N veces con el mismo pedido. Si el gimnasio, la base o el
 * usuario ya existen, los reutiliza; si una corrida anterior se cortó a mitad, la completa. Esto
 * importa porque un alta puede fallar en cualquier paso (se cae la red, se reinicia el servidor).</p>
 *
 * <p>Pasos:</p>
 * <ol>
 *   <li>Valida que ningún dominio pertenezca a OTRO gimnasio (antes de tocar nada).</li>
 *   <li>Crea (o reutiliza) el gimnasio y sus dominios en el catálogo.</li>
 *   <li>Genera una clave aleatoria, la guarda CIFRADA y registra la conexión ({@code cifrado:ID}).</li>
 *   <li>Con gp_aprovisionador: crea la base {@code gym_NNN} y el usuario {@code gym_NNN_app} con
 *       permisos SOLO de datos sobre esa base.</li>
 *   <li>Aplica las migraciones del esquema común y deja el gimnasio ACTIVO.</li>
 * </ol>
 *
 * <p>Los nombres salen del id del gimnasio ({@code gym_%03d}), nunca de un texto del usuario: así
 * funciona igual para el gimnasio 1 que para el 1500.</p>
 */
@Service
public class ServicioAprovisionamiento {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioAprovisionamiento.class);

    /** Mismos formatos que los CHECK de V1: se validan ANTES de armar SQL con ellos. */
    private static final Pattern NOMBRE_BD = Pattern.compile("^gym_[0-9]{3,}$");
    private static final Pattern USUARIO_BD = Pattern.compile("^gym_[0-9]{3,}_app$");
    private static final Pattern CLAVE_SEGURA = Pattern.compile("^[A-Za-z0-9_-]{32,}$");
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final JdbcTemplate catalogo;
    private final CatalogoConexiones conexiones;
    private final ResolutorSecretos secretos;
    private final PoolsPorGimnasio pools;
    private final PropiedadesBd bd;
    private final PropiedadesAprovisionamiento propiedades;
    private final SecureRandom azar = new SecureRandom();

    public ServicioAprovisionamiento(JdbcTemplate catalogo, CatalogoConexiones conexiones, ResolutorSecretos secretos,
            PoolsPorGimnasio pools, PropiedadesBd bd, PropiedadesAprovisionamiento propiedades) {
        this.catalogo = catalogo;
        this.conexiones = conexiones;
        this.secretos = secretos;
        this.pools = pools;
        this.bd = bd;
        this.propiedades = propiedades;
    }

    public ResultadoAlta darDeAlta(AltaGimnasio alta) {
        validar(alta);
        List<String> dominios = normalizarDominios(alta.dominios());
        verificarQueLosDominiosNoSeanDeOtro(alta.slug(), dominios);

        long gimnasioId = asegurarGimnasio(alta);
        dominios.forEach(host -> asegurarDominio(gimnasioId, host));

        String nombreBd = "gym_%03d".formatted(gimnasioId);
        String usuarioBd = nombreBd + "_app";
        exigir(NOMBRE_BD, nombreBd, "nombre de base");
        exigir(USUARIO_BD, usuarioBd, "usuario de base");

        String clave = asegurarConexion(gimnasioId, nombreBd, usuarioBd);
        try {
            crearBaseYUsuario(nombreBd, usuarioBd, clave);
            String version = MigracionesGimnasio.migrar(bd.urlPara(nombreBd), propiedades.usuario(), clavePropia());
            cargarDatosIniciales(nombreBd, alta.nombre());

            conexiones.marcar(gimnasioId, "LISTA", version);
            catalogo.update("UPDATE gimnasio SET estado = 'ACTIVO' WHERE id = ? AND estado = 'ALTA_PENDIENTE'", gimnasioId);
            pools.invalidar(gimnasioId);
            LOG.info("Gimnasio '{}' listo: base {}, esquema v{}", alta.slug(), nombreBd, version);
            return new ResultadoAlta(gimnasioId, nombreBd, version);
        } catch (RuntimeException | SQLException e) {
            conexiones.marcar(gimnasioId, "ERROR", null);
            // El mensaje no incluye la clave: solo qué gimnasio y qué paso.
            throw new IllegalStateException("Falló el alta del gimnasio '" + alta.slug() + "' (" + nombreBd + ")", e);
        }
    }

    // ------------------------------------------------------------------ catálogo

    private long asegurarGimnasio(AltaGimnasio alta) {
        List<Long> existente = catalogo.queryForList("SELECT id FROM gimnasio WHERE slug = ?", Long.class, alta.slug());
        if (!existente.isEmpty()) {
            return existente.getFirst();
        }
        catalogo.update("INSERT INTO gimnasio (slug, nombre) VALUES (?, ?)", alta.slug(), alta.nombre());
        return catalogo.queryForObject("SELECT id FROM gimnasio WHERE slug = ?", Long.class, alta.slug());
    }

    private void verificarQueLosDominiosNoSeanDeOtro(String slug, List<String> dominios) {
        for (String host : dominios) {
            List<String> duenio = catalogo.queryForList("""
                    SELECT g.slug FROM gimnasio_dominio d JOIN gimnasio g ON g.id = d.gimnasio_id WHERE d.host = ?
                    """, String.class, host);
            if (!duenio.isEmpty() && !duenio.getFirst().equals(slug)) {
                throw new IllegalArgumentException("El dominio " + host + " ya pertenece a otro gimnasio");
            }
        }
    }

    private void asegurarDominio(long gimnasioId, String host) {
        catalogo.update("INSERT IGNORE INTO gimnasio_dominio (gimnasio_id, host) VALUES (?, ?)", gimnasioId, host);
    }

    /** Devuelve la clave del usuario de la base: la ya guardada, o una nueva (que guarda cifrada). */
    private String asegurarConexion(long gimnasioId, String nombreBd, String usuarioBd) {
        Optional<DatosConexion> existente = conexiones.buscar(gimnasioId);
        if (existente.isPresent()) {
            return secretos.resolver(existente.get().secretRef());
        }
        String clave = generarClave();
        String referencia = secretos.guardarCifrado(clave);
        conexiones.crear(new DatosConexion(gimnasioId, bd.host(), bd.puerto(), nombreBd, usuarioBd, referencia, "PENDIENTE"));
        return clave;
    }

    // ------------------------------------------------------- servidor MySQL

    private void crearBaseYUsuario(String nombreBd, String usuarioBd, String clave) throws SQLException {
        exigir(CLAVE_SEGURA, clave, "clave generada");
        // CREATE DATABASE / CREATE USER no admiten parámetros "?": por eso los nombres y la clave se
        // validan con patrones estrictos antes de llegar acá. Estas sentencias NUNCA se loguean.
        try (Connection con = conexionAprovisionador(""); Statement sql = con.createStatement()) {
            sql.execute("CREATE DATABASE IF NOT EXISTS `" + nombreBd + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            sql.execute("CREATE USER IF NOT EXISTS '" + usuarioBd + "'@'%' IDENTIFIED BY '" + clave + "'");
            // Si el usuario ya existía (alta anterior cortada), se sincroniza con la clave guardada.
            sql.execute("ALTER USER '" + usuarioBd + "'@'%' IDENTIFIED BY '" + clave + "'");
            // Solo datos (DML) y solo SU base: sin CREATE/ALTER/DROP, sin acceso a otras bases.
            sql.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON `" + nombreBd + "`.* TO '" + usuarioBd + "'@'%'");
        }
    }

    private void cargarDatosIniciales(String nombreBd, String nombreGimnasio) throws SQLException {
        try (Connection con = conexionAprovisionador(nombreBd);
                PreparedStatement sql = con.prepareStatement(
                        // INSERT IGNORE: si el gimnasio ya personalizó el mensaje, no se pisa.
                        "INSERT IGNORE INTO parametro_gimnasio (clave, valor) VALUES ('bienvenida', ?)")) {
            sql.setString(1, "¡Bienvenido a " + nombreGimnasio + "!");
            sql.executeUpdate();
        }
    }

    private Connection conexionAprovisionador(String base) throws SQLException {
        return DriverManager.getConnection(bd.urlPara(base), propiedades.usuario(), clavePropia());
    }

    private String clavePropia() {
        if (propiedades.password() == null || propiedades.password().isBlank()) {
            throw new IllegalStateException("Falta GP_APROVISIONADOR_DB_PASSWORD: sin ella no se pueden dar de alta gimnasios");
        }
        return propiedades.password();
    }

    // ------------------------------------------------------------- utilidades

    /** 32 bytes aleatorios en Base64 URL (A-Z a-z 0-9 - _): 256 bits y sin comillas que escapar. */
    private String generarClave() {
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void validar(AltaGimnasio alta) {
        if (alta.slug() == null || !SLUG.matcher(alta.slug()).matches()) {
            throw new IllegalArgumentException("Slug inválido: solo minúsculas, números y guiones");
        }
        if (alta.nombre() == null || alta.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del gimnasio es obligatorio");
        }
        if (alta.dominios().isEmpty()) {
            throw new IllegalArgumentException("El gimnasio necesita al menos un dominio");
        }
    }

    private static List<String> normalizarDominios(List<String> dominios) {
        List<String> normalizados = new ArrayList<>();
        for (String dominio : dominios) {
            normalizados.add(NormalizadorHost.normalizar(dominio)
                    .orElseThrow(() -> new IllegalArgumentException("Dominio inválido: " + dominio)));
        }
        return normalizados;
    }

    private static void exigir(Pattern patron, String valor, String que) {
        if (!patron.matcher(valor).matches()) {
            throw new IllegalStateException("Formato inválido de " + que);
        }
    }
}
