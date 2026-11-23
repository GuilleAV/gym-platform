package com.gimnasio.identidad;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.gimnasio.plataforma.BaseDelGimnasio;

/**
 * Usuarios del gimnasio ACTUAL (siempre el del contexto: nunca recibe un id de gimnasio).
 */
@Service
public class ServicioUsuarios {

    private final BaseDelGimnasio base;
    private final PasswordEncoder codificador;

    public ServicioUsuarios(BaseDelGimnasio base, PasswordEncoder codificador) {
        this.base = base;
        this.codificador = codificador;
    }

    /** Correos en minúsculas y sin espacios: "Ana@Mail.com " y "ana@mail.com" son la misma cuenta. */
    public static String normalizarEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Crea un usuario ACTIVO si no existe (idempotente). Uso actual: usuarios de demostración.
     * El alta real será por invitación con enlace de un solo uso (INS-04, paso siguiente).
     */
    public void crearActivoSiNoExiste(String email, String nombre, String clave) {
        if (!Contrasenias.largoValido(clave)) {
            throw new IllegalArgumentException("La contraseña debe tener entre " + Contrasenias.LARGO_MINIMO
                    + " y " + Contrasenias.LARGO_MAXIMO + " caracteres");
        }
        base.jdbc().update("INSERT IGNORE INTO usuario (email, nombre, hash_clave, estado) VALUES (?, ?, ?, 'ACTIVO')",
                normalizarEmail(email), nombre, codificador.encode(clave));
    }

    public Optional<String> nombreDe(String email) {
        return base.jdbc().queryForList("SELECT nombre FROM usuario WHERE email = ?", String.class, normalizarEmail(email))
                .stream().findFirst();
    }
}
