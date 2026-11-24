package com.gimnasio.identidad.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.gimnasio.plataforma.ContextoGimnasio;
import com.gimnasio.plataforma.GimnasioActual;

/** Unitario: una sesión iniciada en un gimnasio no sirve en otro (defensa en profundidad). */
class FiltroGimnasioDeLaSesionTest {

    private static final GimnasioActual NORTE = new GimnasioActual(1, "gym-norte", "Gym Norte", ZoneId.of("UTC"));

    private final FiltroGimnasioDeLaSesion filtro = new FiltroGimnasioDeLaSesion();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sesionDelMismoGimnasioPasa() throws Exception {
        MockHttpSession sesion = sesionDelGimnasio(1L);
        MockHttpServletResponse respuesta = filtrar(sesion);

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(sesion.isInvalid()).isFalse();
    }

    @Test
    void sesionDeOtroGimnasioSeInvalidaYDa401() throws Exception {
        MockHttpSession sesion = sesionDelGimnasio(2L);
        MockHttpServletResponse respuesta = filtrar(sesion);

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(sesion.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void sesionAutenticadaSinGimnasioSeInvalida() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        MockHttpServletResponse respuesta = filtrar(sesion);

        assertThat(respuesta.getStatus()).isEqualTo(401);
    }

    private static MockHttpSession sesionDelGimnasio(long gimnasioId) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(FiltroGimnasioDeLaSesion.ATRIBUTO_GIMNASIO, gimnasioId);
        return sesion;
    }

    private MockHttpServletResponse filtrar(MockHttpSession sesion) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("ana@gym.com", null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sesion/yo");
        request.setSession(sesion);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        ContextoGimnasio.ejecutarPara(NORTE, () -> {
            filtro.doFilter(request, respuesta, new MockFilterChain());
            return null;
        });
        return respuesta;
    }
}
