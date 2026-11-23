package com.gimnasio.plataforma.conexiones;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Lee y escribe {@code gym_platform.gimnasio_conexion} (con el usuario gp_app). */
@Repository
public class CatalogoConexiones {

    private static final RowMapper<DatosConexion> FILA = (fila, n) -> new DatosConexion(
            fila.getLong("gimnasio_id"), fila.getString("host_bd"), fila.getInt("puerto_bd"),
            fila.getString("nombre_bd"), fila.getString("usuario_bd"), fila.getString("secret_ref"),
            fila.getString("estado"));

    private final JdbcTemplate jdbc;

    public CatalogoConexiones(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DatosConexion> buscar(long gimnasioId) {
        List<DatosConexion> filas = jdbc.query("SELECT * FROM gimnasio_conexion WHERE gimnasio_id = ?", FILA, gimnasioId);
        return filas.stream().findFirst();
    }

    public void crear(DatosConexion datos) {
        jdbc.update("""
                INSERT INTO gimnasio_conexion (gimnasio_id, host_bd, puerto_bd, nombre_bd, usuario_bd, secret_ref, estado)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, datos.gimnasioId(), datos.hostBd(), datos.puertoBd(), datos.nombreBd(), datos.usuarioBd(),
                datos.secretRef(), datos.estado());
    }

    public void marcar(long gimnasioId, String estado, String versionEsquema) {
        jdbc.update("UPDATE gimnasio_conexion SET estado = ?, version_esquema = COALESCE(?, version_esquema) WHERE gimnasio_id = ?",
                estado, versionEsquema, gimnasioId);
    }
}
