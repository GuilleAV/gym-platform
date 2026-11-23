package com.gimnasio.plataforma.secretos;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Guarda y lee secretos cifrados en {@code gym_platform.secreto}. Nunca ve el texto plano. */
@Repository
public class RepositorioSecretos {

    private final JdbcTemplate jdbc;

    public RepositorioSecretos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long guardar(SecretoCifrado secreto) {
        KeyHolder clave = new GeneratedKeyHolder();
        jdbc.update(conexion -> {
            PreparedStatement sentencia = conexion.prepareStatement(
                    "INSERT INTO secreto (version_clave, iv, cifrado) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            sentencia.setInt(1, secreto.versionClave());
            sentencia.setBytes(2, secreto.iv());
            sentencia.setBytes(3, secreto.cifrado());
            return sentencia;
        }, clave);
        return Objects.requireNonNull(clave.getKey(), "MySQL no devolvió el id del secreto").longValue();
    }

    public SecretoCifrado leer(long id) {
        return jdbc.queryForObject("SELECT version_clave, iv, cifrado FROM secreto WHERE id = ?",
                (fila, n) -> new SecretoCifrado(fila.getInt("version_clave"), fila.getBytes("iv"), fila.getBytes("cifrado")),
                id);
    }
}
