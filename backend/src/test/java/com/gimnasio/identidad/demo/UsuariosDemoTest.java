package com.gimnasio.identidad.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com.gimnasio.identidad.ServicioUsuarios;
import com.gimnasio.plataforma.GimnasioActual;
import com.gimnasio.plataforma.Gimnasios;

/** Unitario (sin base): el perfil demo crea los usuarios de ejemplo con sus roles. */
class UsuariosDemoTest {

    private static final GimnasioActual NORTE = new GimnasioActual(1, "gym-norte", "Gym Norte", ZoneId.of("UTC"));

    @Test
    void creaElUsuarioYLeAsignaLosRolesConfigurados() throws Exception {
        Gimnasios gimnasios = mock(Gimnasios.class);
        ServicioUsuarios usuarios = mock(ServicioUsuarios.class);
        when(gimnasios.activoPorSlug("gym-norte")).thenReturn(Optional.of(NORTE));
        UsuariosDemo.Propiedades propiedades = new UsuariosDemo.Propiedades("clave-demo-larga",
                List.of(new UsuariosDemo.Propiedades.Usuario("gym-norte", "admin@gym-norte.localhost", "Admin", List.of("ADMINISTRADOR"))));

        new UsuariosDemo(propiedades, gimnasios, usuarios).run(new DefaultApplicationArguments());

        verify(usuarios).crearActivoSiNoExiste("admin@gym-norte.localhost", "Admin", "clave-demo-larga");
        verify(usuarios).asignarRol("admin@gym-norte.localhost", "ADMINISTRADOR");
    }

    @Test
    void sinClaveNoCreaNada() throws Exception {
        ServicioUsuarios usuarios = mock(ServicioUsuarios.class);
        UsuariosDemo.Propiedades propiedades = new UsuariosDemo.Propiedades("",
                List.of(new UsuariosDemo.Propiedades.Usuario("gym-norte", "admin@gym-norte.localhost", "Admin", List.of("ADMINISTRADOR"))));

        new UsuariosDemo(propiedades, mock(Gimnasios.class), usuarios).run(new DefaultApplicationArguments());

        verify(usuarios, never()).crearActivoSiNoExiste(anyString(), anyString(), anyString());
    }

    @Test
    void enElYamlLosRolesSonOpcionales() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "gym.demo.clave", "x",
                "gym.demo.usuarios[0].gimnasio", "gym-norte",
                "gym.demo.usuarios[0].email", "a@a.com",
                "gym.demo.usuarios[0].nombre", "A",
                "gym.demo.usuarios[0].roles[0]", "ADMINISTRADOR",
                "gym.demo.usuarios[1].gimnasio", "power-fit",
                "gym.demo.usuarios[1].email", "b@b.com",
                "gym.demo.usuarios[1].nombre", "B")));

        UsuariosDemo.Propiedades p = binder.bind("gym.demo", UsuariosDemo.Propiedades.class).get();

        assertThat(p.usuarios().get(0).roles()).containsExactly("ADMINISTRADOR");
        assertThat(p.usuarios().get(1).roles()).isEmpty();
    }

    @Test
    void elToStringNoMuestraLaClave() {
        assertThat(new UsuariosDemo.Propiedades("secreta", List.of()).toString()).doesNotContain("secreta");
    }
}
