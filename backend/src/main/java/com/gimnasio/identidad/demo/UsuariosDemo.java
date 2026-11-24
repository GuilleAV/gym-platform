package com.gimnasio.identidad.demo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.gimnasio.identidad.ServicioUsuarios;
import com.gimnasio.plataforma.ContextoGimnasio;
import com.gimnasio.plataforma.GimnasioActual;
import com.gimnasio.plataforma.Gimnasios;

/**
 * Perfil demo: crea un usuario administrador en cada gimnasio de ejemplo, con la clave de
 * {@code GYM_DEMO_CLAVE} (en tu .env). Corre DESPUÉS del alta de gimnasios (@Order 20 > 10).
 * Solo para desarrollo: el alta real de usuarios será por invitación.
 */
@Component
@Order(20)
@EnableConfigurationProperties(UsuariosDemo.Propiedades.class)
class UsuariosDemo implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(UsuariosDemo.class);

    @ConfigurationProperties("gym.demo")
    record Propiedades(String clave, @DefaultValue List<Usuario> usuarios) {

        record Usuario(String gimnasio, String email, String nombre) {
        }

        @Override
        public String toString() {
            return "Propiedades[clave=***, usuarios=" + usuarios + "]";
        }
    }

    private final Propiedades propiedades;
    private final Gimnasios gimnasios;
    private final ServicioUsuarios servicio;

    UsuariosDemo(Propiedades propiedades, Gimnasios gimnasios, ServicioUsuarios servicio) {
        this.propiedades = propiedades;
        this.gimnasios = gimnasios;
        this.servicio = servicio;
    }

    @Override
    public void run(ApplicationArguments argumentos) throws Exception {
        if (propiedades.usuarios().isEmpty()) {
            return;
        }
        if (propiedades.clave() == null || propiedades.clave().isBlank()) {
            LOG.warn("Perfil demo sin GYM_DEMO_CLAVE: no se crean usuarios de ejemplo");
            return;
        }
        for (Propiedades.Usuario usuario : propiedades.usuarios()) {
            GimnasioActual gimnasio = gimnasios.activoPorSlug(usuario.gimnasio())
                    .orElseThrow(() -> new IllegalStateException("Gimnasio de demo inexistente: " + usuario.gimnasio()));
            ContextoGimnasio.ejecutarPara(gimnasio, () -> {
                servicio.crearActivoSiNoExiste(usuario.email(), usuario.nombre(), propiedades.clave());
                return null;
            });
            LOG.info("Usuario de demo listo: {} en {}", usuario.email(), usuario.gimnasio());
        }
    }
}
