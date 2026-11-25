package com.gimnasio.identidad;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.gimnasio.plataforma.BaseDelGimnasio;

/**
 * Roles del gimnasio ACTUAL (ADR-0005). Los roles son datos: crear uno o cambiarle el MFA no requiere
 * programar ni desplegar.
 *
 * <p><b>Quién lo usa:</b> solo el panel del <b>superadmin</b> (todavía no existe). Por decisión del dueño
 * del producto, el administrador del gimnasio NO puede cambiar el MFA de los roles. Por eso este servicio
 * no tiene ningún endpoint HTTP hasta que exista la identidad de plataforma con su autorización.</p>
 */
@Service
public class ServicioRoles {

    /** Igual que el CHECK de la base: se valida acá para dar un error claro antes de llegar a MySQL. */
    private static final Pattern CODIGO_VALIDO = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");

    private final BaseDelGimnasio base;

    public ServicioRoles(BaseDelGimnasio base) {
        this.base = base;
    }

    public List<Rol> listar() {
        return base.jdbc().query("SELECT codigo, nombre, mfa, activo FROM rol ORDER BY id",
                (fila, n) -> new Rol(fila.getString("codigo"), fila.getString("nombre"), fila.getBoolean("mfa"),
                        fila.getBoolean("activo")));
    }

    /** Crea un rol nuevo en el gimnasio actual (ej. NUTRICIONISTA). */
    public void crear(String codigo, String nombre, boolean mfa) {
        if (codigo == null || !CODIGO_VALIDO.matcher(codigo).matches()) {
            throw new IllegalArgumentException("Código de rol inválido: usar MAYÚSCULAS, números y _ (2 a 40 caracteres)");
        }
        if (nombre == null || nombre.isBlank() || nombre.length() > 80) {
            throw new IllegalArgumentException("El nombre del rol es obligatorio (hasta 80 caracteres)");
        }
        base.jdbc().update("INSERT INTO rol (codigo, nombre, mfa) VALUES (?, ?, ?)", codigo, nombre.strip(), mfa);
    }

    /**
     * Cambia si el rol exige MFA. Aplica desde el próximo ingreso de cada usuario con ese rol.
     *
     * @return false si el rol no existe
     */
    public boolean cambiarMfa(String codigo, boolean mfa) {
        return base.jdbc().update("UPDATE rol SET mfa = ? WHERE codigo = ?", mfa, codigo) == 1
                || existe(codigo); // si ya tenía ese valor, MySQL informa 0 filas cambiadas
    }

    private boolean existe(String codigo) {
        Boolean existe = base.jdbc().queryForObject("SELECT EXISTS (SELECT 1 FROM rol WHERE codigo = ?)", Boolean.class, codigo);
        return Boolean.TRUE.equals(existe);
    }
}
