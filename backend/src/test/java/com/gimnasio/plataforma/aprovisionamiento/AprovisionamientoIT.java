package com.gimnasio.plataforma.aprovisionamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.gimnasio.plataforma.BaseDelGimnasio;
import com.gimnasio.plataforma.ContextoGimnasio;
import com.gimnasio.plataforma.GimnasioActual;
import com.gimnasio.plataforma.conexiones.CatalogoConexiones;
import com.gimnasio.plataforma.conexiones.DatosConexion;
import com.gimnasio.plataforma.secretos.ResolutorSecretos;
import com.gimnasio.soporte.MysqlPlataformaContenedor;

/**
 * Paso 0.5: alta automática e AISLAMIENTO entre las bases de los gimnasios.
 *
 * <p>Da de alta dos gimnasios una sola vez para toda la clase ({@code PER_CLASS} + {@code @BeforeAll})
 * y después verifica, desde distintos ángulos, que cada uno vive en su base y no puede ver la otra.
 * Usa slugs propios ("alta-...") para no chocar con los datos de otros tests.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AprovisionamientoIT extends MysqlPlataformaContenedor {

    private static final AltaGimnasio NORTE = new AltaGimnasio("alta-norte", "Alta Norte", List.of("alta-norte.localhost"));
    private static final AltaGimnasio SUR = new AltaGimnasio("alta-sur", "Alta Sur", List.of("Alta-Sur.localhost"));

    @Autowired
    ServicioAprovisionamiento servicio;

    @Autowired
    CatalogoConexiones conexiones;

    @Autowired
    ResolutorSecretos secretos;

    @Autowired
    BaseDelGimnasio base;

    @Autowired
    JdbcTemplate catalogo;

    @Autowired
    MockMvc mvc;

    private ResultadoAlta norte;
    private ResultadoAlta sur;

    @BeforeAll
    void darDeAlta() {
        norte = servicio.darDeAlta(NORTE);
        sur = servicio.darDeAlta(SUR);
    }

    // ------------------------------------------------------------- el alta

    @Test
    void creaUnaBaseYUnUsuarioPorGimnasio() throws SQLException {
        assertThat(norte.nombreBd()).isEqualTo("gym_%03d".formatted(norte.gimnasioId()));
        assertThat(sur.nombreBd()).isNotEqualTo(norte.nombreBd());

        List<String> bases = consultarComoRoot("SHOW DATABASES");
        assertThat(bases).contains(norte.nombreBd(), sur.nombreBd());
        List<String> usuarios = consultarComoRoot("SELECT user FROM mysql.user");
        assertThat(usuarios).contains(norte.nombreBd() + "_app", sur.nombreBd() + "_app");
    }

    @Test
    void dejaElGimnasioActivoYLaConexionLista() {
        assertThat(catalogo.queryForObject("SELECT estado FROM gimnasio WHERE id = ?", String.class, norte.gimnasioId()))
                .isEqualTo("ACTIVO");
        DatosConexion conexion = conexiones.buscar(norte.gimnasioId()).orElseThrow();
        assertThat(conexion.estado()).isEqualTo("LISTA");
        assertThat(norte.versionEsquema()).as("última migración de db/gimnasio").isEqualTo("2");
    }

    @Test
    void elDominioQuedaNormalizado() {
        assertThat(catalogo.queryForList("SELECT host FROM gimnasio_dominio WHERE gimnasio_id = ?", String.class, sur.gimnasioId()))
                .containsExactly("alta-sur.localhost");
    }

    // --------------------------------------------------------- la clave

    @Test
    void laClaveSeGuardaCifradaYNuncaEnTextoPlano() {
        DatosConexion conexion = conexiones.buscar(norte.gimnasioId()).orElseThrow();
        assertThat(conexion.secretRef()).startsWith("cifrado:");

        String clave = secretos.resolver(conexion.secretRef());
        assertThat(clave).hasSizeGreaterThanOrEqualTo(32);

        byte[] cifrado = catalogo.queryForObject("SELECT cifrado FROM secreto WHERE id = ?", byte[].class,
                Long.parseLong(conexion.secretRef().substring("cifrado:".length())));
        assertThat(new String(cifrado, StandardCharsets.ISO_8859_1)).doesNotContain(clave);
    }

    @Test
    void cadaGimnasioTieneUnaClaveDistinta() {
        String claveNorte = secretos.resolver(conexiones.buscar(norte.gimnasioId()).orElseThrow().secretRef());
        String claveSur = secretos.resolver(conexiones.buscar(sur.gimnasioId()).orElseThrow().secretRef());
        assertThat(claveNorte).isNotEqualTo(claveSur);
    }

    // ------------------------------------------------------- idempotencia

    @Test
    void repetirElAltaNoDuplicaNiCambiaNada() {
        String referenciaAntes = conexiones.buscar(norte.gimnasioId()).orElseThrow().secretRef();
        Integer migracionesAntes = migracionesAplicadas(norte.nombreBd());

        ResultadoAlta otraVez = servicio.darDeAlta(NORTE);

        assertThat(otraVez).isEqualTo(norte);
        assertThat(conexiones.buscar(norte.gimnasioId()).orElseThrow().secretRef()).isEqualTo(referenciaAntes);
        assertThat(migracionesAplicadas(norte.nombreBd())).isEqualTo(migracionesAntes);
        assertThat(catalogo.queryForObject("SELECT COUNT(*) FROM gimnasio WHERE slug = 'alta-norte'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void noSePuedeRobarElDominioDeOtroGimnasio() {
        AltaGimnasio intruso = new AltaGimnasio("alta-intruso", "Intruso", List.of("alta-norte.localhost"));

        assertThatThrownBy(() -> servicio.darDeAlta(intruso))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("otro gimnasio");
        assertThat(catalogo.queryForObject("SELECT COUNT(*) FROM gimnasio WHERE slug = 'alta-intruso'", Integer.class))
                .as("no deja un gimnasio a medio crear")
                .isZero();
    }

    // ------------------------------------------------------------ AISLAMIENTO

    @Test
    void elUsuarioDeUnGimnasioLeeSuBasePeroNoLaDeOtro() throws SQLException {
        try (Connection comoNorte = conexionComo(norte); Statement sql = comoNorte.createStatement()) {
            try (ResultSet propio = sql.executeQuery("SELECT COUNT(*) FROM parametro_gimnasio")) {
                assertThat(propio.next()).isTrue();
            }
            assertThatThrownBy(() -> sql.executeQuery("SELECT * FROM `" + sur.nombreBd() + "`.parametro_gimnasio"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> sql.executeQuery("SELECT * FROM gym_platform.secreto"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
        }
    }

    @Test
    void elUsuarioDeUnGimnasioNoPuedeCambiarLaEstructura() throws SQLException {
        try (Connection comoNorte = conexionComo(norte); Statement sql = comoNorte.createStatement()) {
            assertThatThrownBy(() -> sql.execute("CREATE TABLE intruso (id INT)"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("denied");
            assertThatThrownBy(() -> sql.execute("DROP TABLE parametro_gimnasio"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("denied");
        }
    }

    @Test
    void laBaseDelGimnasioActualEsLaDeSuContexto() throws Exception {
        String desdeNorte = ContextoGimnasio.ejecutarPara(gimnasio(norte, NORTE),
                () -> base.jdbc().queryForObject("SELECT DATABASE()", String.class));
        String desdeSur = ContextoGimnasio.ejecutarPara(gimnasio(sur, SUR),
                () -> base.jdbc().queryForObject("SELECT DATABASE()", String.class));

        assertThat(desdeNorte).isEqualTo(norte.nombreBd());
        assertThat(desdeSur).isEqualTo(sur.nombreBd());
    }

    @Test
    void sinGimnasioEnElContextoNoHayBase() {
        assertThatThrownBy(() -> base.jdbc()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void porHttpCadaDominioLeeDeSuPropiaBase() throws Exception {
        mvc.perform(get("http://alta-norte.localhost/api/gimnasio/bienvenida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("¡Bienvenido a Alta Norte!"));
        mvc.perform(get("http://alta-sur.localhost/api/gimnasio/bienvenida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("¡Bienvenido a Alta Sur!"));
    }

    // ------------------------------------------------------------- utilidades

    private Connection conexionComo(ResultadoAlta alta) throws SQLException {
        DatosConexion datos = conexiones.buscar(alta.gimnasioId()).orElseThrow();
        return DriverManager.getConnection(
                "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + datos.nombreBd()
                        + "?sslMode=PREFERRED&allowPublicKeyRetrieval=true",
                datos.usuarioBd(), secretos.resolver(datos.secretRef()));
    }

    private static GimnasioActual gimnasio(ResultadoAlta alta, AltaGimnasio pedido) {
        return new GimnasioActual(alta.gimnasioId(), pedido.slug(), pedido.nombre(), ZoneId.of("UTC"));
    }

    private Integer migracionesAplicadas(String nombreBd) {
        try (Connection root = conexionRoot(); Statement sql = root.createStatement();
                ResultSet fila = sql.executeQuery("SELECT COUNT(*) FROM `" + nombreBd + "`.flyway_schema_history")) {
            fila.next();
            return fila.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> consultarComoRoot(String consulta) throws SQLException {
        try (Connection root = conexionRoot(); Statement sql = root.createStatement(); ResultSet filas = sql.executeQuery(consulta)) {
            List<String> valores = new java.util.ArrayList<>();
            while (filas.next()) {
                valores.add(filas.getString(1));
            }
            return valores;
        }
    }
}
