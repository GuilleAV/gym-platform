package com.gimnasio.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.gimnasio.plataforma.BaseDelGimnasio;
import com.gimnasio.plataforma.ContextoGimnasio;
import com.gimnasio.plataforma.GimnasioActual;
import com.gimnasio.plataforma.aprovisionamiento.AltaGimnasio;
import com.gimnasio.plataforma.aprovisionamiento.ResultadoAlta;
import com.gimnasio.plataforma.aprovisionamiento.ServicioAprovisionamiento;
import com.gimnasio.plataforma.sesiones.LimpiezaDeSesiones;
import com.gimnasio.soporte.MysqlPlataformaContenedor;

import jakarta.servlet.http.Cookie;

/**
 * Paso 0.6: inicio de sesión de punta a punta (HTTP → Spring Security → base del gimnasio).
 *
 * <p>Casos de la regla de oro: camino feliz, validación, sin autenticación, sin CSRF y
 * <b>otro gimnasio</b>.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SesionIT extends MysqlPlataformaContenedor {

    private static final String CLAVE = "clave-larga-de-prueba-123";
    private static final String NORTE = "ses-norte.localhost";
    private static final String SUR = "ses-sur.localhost";

    @Autowired MockMvc mvc;
    @Autowired ServicioAprovisionamiento aprovisionamiento;
    @Autowired ServicioUsuarios usuarios;
    @Autowired BaseDelGimnasio base;
    @Autowired LimpiezaDeSesiones limpieza;
    @Autowired JdbcTemplate catalogo;

    private GimnasioActual norte;
    private GimnasioActual sur;

    @BeforeAll
    void gimnasiosYUsuarios() throws Exception {
        norte = alta("ses-norte", "Ses Norte", NORTE);
        sur = alta("ses-sur", "Ses Sur", SUR);
        enGimnasio(norte, () -> usuarios.crearActivoSiNoExiste("ana@correo.com", "Ana", CLAVE));
        enGimnasio(norte, () -> usuarios.crearActivoSiNoExiste("susi@correo.com", "Susi", CLAVE));
        enGimnasio(norte, () -> base.jdbc().update("UPDATE usuario SET estado = 'SUSPENDIDO' WHERE email = 'susi@correo.com'"));
    }

    // ------------------------------------------------------------ camino feliz

    @Test
    void ingresarConsultarYSalir() throws Exception {
        Cookie sesion = ingresar(NORTE, "ana@correo.com", CLAVE);

        mvc.perform(get("http://" + NORTE + "/api/sesion/yo").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@correo.com"))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.gimnasio").value("ses-norte"));

        Cookie csrf = cookieCsrf(NORTE);
        mvc.perform(post("http://" + NORTE + "/api/sesion/salir").cookie(sesion, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());

        mvc.perform(get("http://" + NORTE + "/api/sesion/yo").cookie(sesion)).andExpect(status().isUnauthorized());
    }

    @Test
    void elCorreoNoDistingueMayusculas() throws Exception {
        ingresar(NORTE, "  ANA@Correo.com ", CLAVE);
    }

    @Test
    void laCookieDeSesionTieneTodasLasProteccionesYNingunDominio() throws Exception {
        MvcResult resultado = intentarIngresar(NORTE, "ana@correo.com", CLAVE).andExpect(status().isNoContent()).andReturn();

        String setCookie = resultado.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith("__Host-SESION=")).findFirst().orElseThrow();
        assertThat(setCookie).contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/");
        assertThat(setCookie.toLowerCase()).doesNotContain("domain=");
    }

    @Test
    void laSesionSeGuardaEnLaBaseDeSuGimnasio() throws Exception {
        int antesNorte = sesionesEn(norte);
        int antesSur = sesionesEn(sur);

        ingresar(NORTE, "ana@correo.com", CLAVE);

        assertThat(sesionesEn(norte)).isEqualTo(antesNorte + 1);
        assertThat(sesionesEn(sur)).as("la base del otro gimnasio no se toca").isEqualTo(antesSur);
    }

    @Test
    void laContraseniaSeGuardaConArgon2id() throws Exception {
        String hash = enGimnasio(norte, () -> base.jdbc().queryForObject(
                "SELECT hash_clave FROM usuario WHERE email = 'ana@correo.com'", String.class));
        assertThat(hash).startsWith("$argon2id$").doesNotContain(CLAVE);
    }

    // --------------------------------------------------------- sin autenticación

    @Test
    void sinSesionNoSeAccedeAEndpointsPrivados() throws Exception {
        mvc.perform(get("http://" + NORTE + "/api/sesion/yo")).andExpect(status().isUnauthorized());
        mvc.perform(get("http://" + NORTE + "/api/cualquier-otra-cosa")).andExpect(status().isUnauthorized());
    }

    @Test
    void losEndpointsPublicosSiguenAbiertos() throws Exception {
        mvc.perform(get("http://" + NORTE + "/api/gimnasio")).andExpect(status().isOk());
        mvc.perform(get("http://" + NORTE + "/api/gimnasio/bienvenida")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------- credenciales

    @Test
    void claveIncorrectaCorreoInexistenteYCuentaSuspendidaRespondenIgual() throws Exception {
        String claveMala = intentarIngresar(NORTE, "ana@correo.com", "otra-clave-incorrecta")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String inexistente = intentarIngresar(NORTE, "nadie@correo.com", CLAVE)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String suspendida = intentarIngresar(NORTE, "susi@correo.com", CLAVE)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(inexistente).as("no revela si el correo existe").isEqualTo(claveMala);
        assertThat(suspendida).as("no revela el estado de la cuenta").isEqualTo(claveMala);
    }

    @Test
    void datosIncompletosOClaveGiganteDan400() throws Exception {
        intentarIngresar(NORTE, "", CLAVE).andExpect(status().isBadRequest());
        intentarIngresar(NORTE, "ana@correo.com", "x".repeat(10_000)).andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------- CSRF

    @Test
    void sinTokenCsrfElIngresoSeRechaza() throws Exception {
        mvc.perform(post("http://" + NORTE + "/api/sesion/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("ana@correo.com", CLAVE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void conUnTokenCsrfInventadoElIngresoSeRechaza() throws Exception {
        Cookie csrf = cookieCsrf(NORTE);
        mvc.perform(post("http://" + NORTE + "/api/sesion/ingresar")
                        .cookie(csrf).header("X-XSRF-TOKEN", "inventado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("ana@correo.com", CLAVE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void laCookieCsrfTienePrefijoHostYEsSegura() throws Exception {
        MvcResult resultado = mvc.perform(get("http://" + NORTE + "/api/sesion/csrf")).andReturn();
        String setCookie = resultado.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith("__Host-XSRF-TOKEN=")).findFirst().orElseThrow();
        assertThat(setCookie).contains("Secure", "Path=/", "SameSite=Strict");
        assertThat(setCookie.toLowerCase()).doesNotContain("domain=", "httponly");
    }

    // ------------------------------------------------------------ OTRO GIMNASIO

    @Test
    void laCookieDeUnGimnasioNoSirveEnOtro() throws Exception {
        Cookie sesionNorte = ingresar(NORTE, "ana@correo.com", CLAVE);

        mvc.perform(get("http://" + SUR + "/api/sesion/yo").cookie(sesionNorte))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unUsuarioDeUnGimnasioNoPuedeIngresarEnOtro() throws Exception {
        intentarIngresar(SUR, "ana@correo.com", CLAVE).andExpect(status().isUnauthorized());
    }

    // ----------------------------------------------------- base y limpieza

    @Test
    void laBaseRechazaClavesQueNoSeanArgon2id() {
        assertThatThrownBy(() -> enGimnasio(norte, () -> base.jdbc().update(
                "INSERT INTO usuario (email, nombre, hash_clave, estado) VALUES ('texto@plano.com', 'X', 'clave-en-texto-plano', 'ACTIVO')")))
                .rootCause().hasMessageContaining("Check constraint");
    }

    @Test
    void laLimpiezaBorraSesionesVencidasDeCadaGimnasio() throws Exception {
        String vencida = UUID.randomUUID().toString();
        enGimnasio(sur, () -> base.jdbc().update("""
                INSERT INTO sesion (PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, EXPIRY_TIME)
                VALUES (?, ?, 0, 0, 1800, 1)
                """, vencida, UUID.randomUUID().toString()));

        assertThat(limpieza.limpiar()).isGreaterThanOrEqualTo(1);
        Integer quedan = enGimnasio(sur, () -> base.jdbc().queryForObject(
                "SELECT COUNT(*) FROM sesion WHERE PRIMARY_ID = ?", Integer.class, vencida));
        assertThat(quedan).isZero();
    }

    // ------------------------------------------------------------- utilidades

    private Cookie cookieCsrf(String host) throws Exception {
        Cookie csrf = mvc.perform(get("http://" + host + "/api/sesion/csrf"))
                .andExpect(status().isNoContent()).andReturn().getResponse().getCookie("__Host-XSRF-TOKEN");
        assertThat(csrf).as("cookie CSRF").isNotNull();
        return csrf;
    }

    private org.springframework.test.web.servlet.ResultActions intentarIngresar(String host, String email, String clave)
            throws Exception {
        Cookie csrf = cookieCsrf(host);
        return mvc.perform(post("http://" + host + "/api/sesion/ingresar")
                .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(email, clave)));
    }

    private Cookie ingresar(String host, String email, String clave) throws Exception {
        Cookie sesion = intentarIngresar(host, email, clave).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("__Host-SESION");
        assertThat(sesion).as("cookie de sesión").isNotNull();
        return sesion;
    }

    private static String json(String email, String clave) {
        return "{\"email\":\"" + email + "\",\"clave\":\"" + clave + "\"}";
    }

    private int sesionesEn(GimnasioActual gimnasio) throws Exception {
        return enGimnasio(gimnasio, () -> base.jdbc().queryForObject("SELECT COUNT(*) FROM sesion", Integer.class));
    }

    private GimnasioActual alta(String slug, String nombre, String host) {
        ResultadoAlta resultado = aprovisionamiento.darDeAlta(new AltaGimnasio(slug, nombre, List.of(host)));
        return new GimnasioActual(resultado.gimnasioId(), slug, nombre, ZoneId.of("UTC"));
    }

    private static <T> T enGimnasio(GimnasioActual gimnasio, java.util.concurrent.Callable<T> operacion) throws Exception {
        return ContextoGimnasio.ejecutarPara(gimnasio, operacion);
    }

    private static void enGimnasio(GimnasioActual gimnasio, Runnable operacion) throws Exception {
        ContextoGimnasio.ejecutarPara(gimnasio, () -> {
            operacion.run();
            return null;
        });
    }
}
