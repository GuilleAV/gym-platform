package com.gimnasio.identidad.seguridad;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.gimnasio.identidad.ServicioUsuarios;
import com.gimnasio.plataforma.BaseDelGimnasio;

/**
 * Le dice a Spring Security cómo buscar un usuario: en la base del gimnasio ACTUAL.
 *
 * <p>Un usuario de Gym Norte que intenta entrar por power-fit.localhost "no existe", porque se lo
 * busca en otra base. No hay forma de cruzar gimnasios en el login.</p>
 */
@Component
class UsuariosDelGimnasio implements UserDetailsService {

    private final BaseDelGimnasio base;

    UsuariosDelGimnasio(BaseDelGimnasio base) {
        this.base = base;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String correo = ServicioUsuarios.normalizarEmail(email);
        // Los roles ACTIVOS se convierten en "authorities" ROLE_<CODIGO> (ADR-0007 §3): es lo que Spring
        // Security compara en reglas como hasAnyRole("ADMINISTRADOR", "RECEPCION").
        List<SimpleGrantedAuthority> roles = base.jdbc().queryForList(
                "SELECT r.codigo FROM rol r JOIN usuario_rol ur ON ur.rol_id = r.id JOIN usuario u ON u.id = ur.usuario_id"
                        + " WHERE u.email = ? AND r.activo",
                String.class, correo).stream().map(codigo -> new SimpleGrantedAuthority("ROLE_" + codigo)).toList();
        List<UserDetails> encontrados = base.jdbc().query(
                "SELECT email, hash_clave, estado FROM usuario WHERE email = ?",
                (fila, n) -> User.withUsername(fila.getString("email"))
                        // Sin contraseña (opcional, ADR-0006): "" nunca coincide con nada en Argon2id.
                        .password(fila.getString("hash_clave") == null ? "" : fila.getString("hash_clave"))
                        .disabled(!"ACTIVO".equals(fila.getString("estado")))
                        .authorities(roles)
                        .build(),
                correo);
        if (encontrados.isEmpty()) {
            // El mensaje NO llega al cliente: el login responde lo mismo exista o no el correo.
            throw new UsernameNotFoundException("usuario inexistente");
        }
        return encontrados.getFirst();
    }
}
