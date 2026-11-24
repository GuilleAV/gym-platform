package com.gimnasio.identidad.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Unitario: la sesión vence a las 12 h del ingreso aunque se use sin parar. */
class FiltroVencimientoAbsolutoTest {

    private static final Instant INGRESO = Instant.parse("2026-11-23T10:00:00Z");
    private static final Duration DOCE_HORAS = Duration.ofHours(12);

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dentroDeLas12HorasPasa() throws Exception {
        MockHttpSession sesion = sesionIngresadaEn(INGRESO);
        MockHttpServletResponse respuesta = filtrar(sesion, INGRESO.plus(DOCE_HORAS).minusSeconds(1));

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(sesion.isInvalid()).isFalse();
    }

    @Test
    void alCumplirse12HorasSeInvalidaYDa401() throws Exception {
        MockHttpSession sesion = sesionIngresadaEn(INGRESO);
        MockHttpServletResponse respuesta = filtrar(sesion, INGRESO.plus(DOCE_HORAS));

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(sesion.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(respuesta.getContentAsString()).doesNotContain("Exception");
    }

    @Test
    void unaSesionAutenticadaSinFechaDeIngresoSeTrataComoVencida() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        assertThat(filtrar(sesion, INGRESO).getStatus()).isEqualTo(401);
    }

    @Test
    void sinAutenticacionNoHaceNada() throws Exception {
        // Un visitante anónimo (sin login) no tiene fecha de ingreso: el filtro no debe molestarlo.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/gimnasio");
        request.setSession(new MockHttpSession());
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        new FiltroVencimientoAbsoluto(relojEn(INGRESO), DOCE_HORAS).doFilter(request, respuesta, new MockFilterChain());

        assertThat(respuesta.getStatus()).isEqualTo(200);
    }

    private static MockHttpSession sesionIngresadaEn(Instant ingreso) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(FiltroVencimientoAbsoluto.ATRIBUTO_INGRESO, ingreso.toEpochMilli());
        return sesion;
    }

    private static MockHttpServletResponse filtrar(MockHttpSession sesion, Instant ahora) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("ana@gym.com", null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sesion/yo");
        request.setSession(sesion);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        new FiltroVencimientoAbsoluto(relojEn(ahora), DOCE_HORAS).doFilter(request, respuesta, new MockFilterChain());
        return respuesta;
    }

    private static Clock relojEn(Instant instante) {
        return Clock.fixed(instante, ZoneOffset.UTC);
    }
}
