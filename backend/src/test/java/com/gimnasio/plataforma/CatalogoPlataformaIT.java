package com.gimnasio.plataforma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.gimnasio.soporte.MysqlPlataformaContenedor;

/**
 * Paso 0.3: base gym_platform creada por Flyway y usuarios con mínimo privilegio (ADR-0002 §3).
 *
 * <p>El {@link JdbcTemplate} inyectado usa el MISMO pool que la aplicación (usuario gp_app).
 * Por eso estos tests prueban lo que la app puede y, sobre todo, lo que NO puede hacer.</p>
 *
 * <p>{@code @Transactional} en un test = cada test corre en una transacción que se deshace
 * (rollback) al terminar. Los datos que inserta un test no ensucian al siguiente.</p>
 */
@SpringBootTest
@Transactional
class CatalogoPlataformaIT extends MysqlPlataformaContenedor {

    @Autowired
    JdbcTemplate jdbc;

    // ------------------------------------------------------------------ Flyway

    @Test
    void laAplicacionArrancaYFlywayAplicoLaV1() {
        Boolean exito = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);
        assertThat(exito).as("la migración V1 del catálogo se aplicó bien").isTrue();
    }

    @Test
    void existenLasTablasDelCatalogo() {
        List<String> tablas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'gym_platform'",
                String.class);
        assertThat(tablas).contains("gimnasio", "gimnasio_dominio", "gimnasio_conexion", "secreto");
    }

    // ------------------------------------------- mínimo privilegio (gp_app)

    @Test
    void laAplicacionNoPuedeCrearTablas() {
        assertThatThrownBy(() -> jdbc.execute("CREATE TABLE intruso (id INT)"))
                .as("gp_app no tiene permisos de estructura (DDL)")
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("denied");
    }

    @Test
    void laAplicacionNoPuedeBorrarTablas() {
        assertThatThrownBy(() -> jdbc.execute("DROP TABLE gimnasio"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("denied");
    }

    @Test
    void laAplicacionNoPuedeCrearBasesNiUsuarios() {
        assertThatThrownBy(() -> jdbc.execute("CREATE DATABASE gym_999"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("denied");
        assertThatThrownBy(() -> jdbc.execute("CREATE USER 'colado'@'%' IDENTIFIED BY 'x'"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("denied");
    }

    @Test
    void laAplicacionSoloVeLaBaseDelCatalogo() {
        List<String> bases = jdbc.queryForList("SHOW DATABASES", String.class);
        assertThat(bases)
                .contains("gym_platform")
                .doesNotContain("mysql", "sys", "test");
    }

    // ----------------------------------------- restricciones de los datos

    @Test
    void sePuedeRegistrarUnGimnasioConSuDominio() {
        long id = insertarGimnasio("gym-norte");
        jdbc.update("INSERT INTO gimnasio_dominio (gimnasio_id, host) VALUES (?, ?)", id, "gym-norte.localhost");

        Long encontrado = jdbc.queryForObject(
                "SELECT gimnasio_id FROM gimnasio_dominio WHERE host = ?", Long.class, "gym-norte.localhost");
        assertThat(encontrado).isEqualTo(id);
    }

    @Test
    void unDominioNoPuedePertenecerADosGimnasios() {
        long norte = insertarGimnasio("gym-norte");
        long powerFit = insertarGimnasio("power-fit");
        jdbc.update("INSERT INTO gimnasio_dominio (gimnasio_id, host) VALUES (?, ?)", norte, "compartido.localhost");

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO gimnasio_dominio (gimnasio_id, host) VALUES (?, ?)", powerFit, "compartido.localhost"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void elDominioDebeGuardarseNormalizado() {
        long id = insertarGimnasio("gym-norte");
        for (String hostInvalido : List.of("Gym-Norte.localhost", "gym-norte.localhost.", "gym-norte.localhost:8080")) {
            assertThatThrownBy(() -> jdbc.update(
                            "INSERT INTO gimnasio_dominio (gimnasio_id, host) VALUES (?, ?)", id, hostInvalido))
                    .as("host inválido: %s", hostInvalido)
                    .satisfies(CatalogoPlataformaIT::esViolacionDeCheck);
        }
    }

    @Test
    void laConexionSoloAceptaNombresYReferenciasConFormatoSeguro() {
        long id = insertarGimnasio("gym-norte");
        String sql = "INSERT INTO gimnasio_conexion (gimnasio_id, host_bd, puerto_bd, nombre_bd, usuario_bd, secret_ref)"
                + " VALUES (?, '127.0.0.1', 3306, ?, ?, ?)";

        assertThatThrownBy(() -> jdbc.update(sql, id, "gym_001; DROP TABLE x", "gym_001_app", "cifrado:1"))
                .as("nombre de base con formato libre")
                .satisfies(CatalogoPlataformaIT::esViolacionDeCheck);
        assertThatThrownBy(() -> jdbc.update(sql, id, "gym_001", "root", "cifrado:1"))
                .as("usuario que no es el del gimnasio")
                .satisfies(CatalogoPlataformaIT::esViolacionDeCheck);
        assertThatThrownBy(() -> jdbc.update(sql, id, "gym_001", "gym_001_app", "clave-en-texto-plano"))
                .as("una clave en vez de una referencia")
                .satisfies(CatalogoPlataformaIT::esViolacionDeCheck);

        assertThat(jdbc.update(sql, id, "gym_001", "gym_001_app", "cifrado:1")).isEqualTo(1);
    }

    /**
     * MySQL informa una restricción CHECK violada con el error 3819. Spring 7 no lo clasifica como
     * DataIntegrityViolationException (lo deja como UncategorizedSQLException), así que verificamos
     * la causa raíz: que el rechazo venga de la base y por una regla CHECK, no por otro error.
     */
    private static void esViolacionDeCheck(Throwable error) {
        assertThat(error).isInstanceOf(DataAccessException.class);
        assertThat(error).rootCause()
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("Check constraint");
    }

    private long insertarGimnasio(String slug) {
        jdbc.update("INSERT INTO gimnasio (slug, nombre) VALUES (?, ?)", slug, "Gimnasio " + slug);
        return jdbc.queryForObject("SELECT id FROM gimnasio WHERE slug = ?", Long.class, slug);
    }
}
