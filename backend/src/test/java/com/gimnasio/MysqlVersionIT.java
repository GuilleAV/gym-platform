package com.gimnasio;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test de INTEGRACIÓN (sufijo IT → lo corre Failsafe en "mvn verify"). Requiere Docker.
 *
 * <p>Levanta un MySQL real con la imagen fijada en el pom y comprueba que el servidor
 * responde con la versión 8.4. Es la base de todos los tests de integración que vienen
 * (repositorios, migraciones Flyway, aislamiento entre gimnasios).</p>
 *
 * <p>{@code static} en el contenedor = se levanta UNA vez para toda la clase, no una por test.
 * Arrancar MySQL tarda varios segundos; esto mantiene la suite rápida.</p>
 */
@Testcontainers
class MysqlVersionIT {

    @Container
    static final MySQLContainer MYSQL =
            new MySQLContainer(DockerImageName.parse(System.getProperty("mysql.image")));

    @Test
    void elServidorMysqlEsVersion84() throws SQLException {
        try (Connection conexion = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery("SELECT VERSION()")) {

            assertThat(resultado.next()).isTrue();
            assertThat(resultado.getString(1))
                    .as("ADR-0001 fija MySQL 8.4 LTS")
                    .startsWith("8.4.");
        }
    }
}
