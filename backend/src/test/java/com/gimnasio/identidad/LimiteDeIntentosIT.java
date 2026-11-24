package com.gimnasio.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.gimnasio.plataforma.BaseDelGimnasio;
import com.gimnasio.plataforma.ContextoGimnasio;
import com.gimnasio.plataforma.GimnasioActual;
import com.gimnasio.plataforma.aprovisionamiento.AltaGimnasio;
import com.gimnasio.plataforma.aprovisionamiento.ResultadoAlta;
import com.gimnasio.plataforma.aprovisionamiento.ServicioAprovisionamiento;
import com.gimnasio.soporte.MysqlPlataformaContenedor;

import jakarta.servlet.http.Cookie;

/**
 * Paso 0.6b: límite de intentos de ingreso, de punta a punta con MySQL real.
 *
 * <p>Cada test usa sus propios correos e IPs para no depender del orden de ejecución: los contadores
 * quedan guardados en la base y un test no debe heredar los fallos de otro.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LimiteDeIntentosIT extends MysqlPlataformaContenedor {

    private static final String CLAVE = "clave-larga-de-prueba-123";
    private static final String MALA = "clave-equivocada-xyz";
    private static final String NORTE = "lim-norte.localhost";
    private static final String SUR = "lim-sur.localhost";
    private static final int FALLOS_POR_CUENTA = 5;   // application.yml
    private static final int FALLOS_POR_IP = 20;      // application.yml

    @Autowired MockMvc mvc;
    @Autowired ServicioAprovisionamiento aprovisionamiento;
    @Autowired ServicioUsuarios usuarios;
    @Autowired BaseDelGimnasio base;

    private GimnasioActual norte;
    private GimnasioActual sur;

    @BeforeAll
    void gimnasios() {
        norte = alta("lim-norte", "Lim Norte", NORTE);
        sur = alta("lim-sur", "Lim Sur", SUR);
    }

    @Test
    void despuesDeCincoFallosLaCuentaSeBloqueaYNiLaClaveCorrectaEntra() throws Exception {
        crearUsuario(norte, "bloq@correo.com");
        for (int i = 0; i < FALLOS_POR_CUENTA; i++) {
            intentar(NORTE, "bloq@correo.com", MALA, "10.0.0.1").andExpect(status().isUnauthorized());
        }

        MvcResult bloqueado = intentar(NORTE, "bloq@correo.com", CLAVE, "10.0.0.1")
                .andExpect(status().isTooManyRequests()).andReturn();

        long reintentarEn = Long.parseLong(bloqueado.getResponse().getHeader("Retry-After"));
        assertThat(reintentarEn).as("primer bloqueo: 1 minuto").isBetween(1L, 60L);
        assertThat(bloqueado.getResponse().getCookie("__Host-SESION")).as("no se creó sesión").isNull();
    }

    @Test
    void elBloqueoNoRevelaSiElCorreoExiste() throws Exception {
        crearUsuario(norte, "existe@correo.com");
        for (int i = 0; i < FALLOS_POR_CUENTA; i++) {
            intentar(NORTE, "existe@correo.com", MALA, "10.0.1.1");
            intentar(NORTE, "no-existe@correo.com", MALA, "10.0.1.2");
        }

        String existe = intentar(NORTE, "existe@correo.com", MALA, "10.0.1.1")
                .andExpect(status().isTooManyRequests()).andReturn().getResponse().getContentAsString();
        String noExiste = intentar(NORTE, "no-existe@correo.com", MALA, "10.0.1.2")
                .andExpect(status().isTooManyRequests()).andReturn().getResponse().getContentAsString();
        assertThat(noExiste).isEqualTo(existe);
    }

    @Test
    void elBloqueoDeUnaCuentaNoAfectaAOtraCuentaDesdeLaMismaIp() throws Exception {
        crearUsuario(norte, "victima@correo.com");
        crearUsuario(norte, "vecina@correo.com");
        for (int i = 0; i < FALLOS_POR_CUENTA; i++) {
            intentar(NORTE, "victima@correo.com", MALA, "10.0.2.1");
        }
        intentar(NORTE, "victima@correo.com", CLAVE, "10.0.2.1").andExpect(status().isTooManyRequests());

        intentar(NORTE, "vecina@correo.com", CLAVE, "10.0.2.1").andExpect(status().isNoContent());
    }

    @Test
    void muchosFallosDesdeUnaIpConCorreosDistintosBloqueanEsaIp() throws Exception {
        // "Password spraying": una clave común contra muchos correos. El límite por cuenta no lo ve.
        crearUsuario(norte, "spray@correo.com");
        for (int i = 0; i < FALLOS_POR_IP; i++) {
            intentar(NORTE, "persona" + i + "@correo.com", "123456", "10.0.3.1").andExpect(status().isUnauthorized());
        }

        intentar(NORTE, "spray@correo.com", CLAVE, "10.0.3.1").andExpect(status().isTooManyRequests());
        // Desde otra IP sí entra: se bloqueó la IP atacante, no la cuenta.
        intentar(NORTE, "spray@correo.com", CLAVE, "10.0.3.2").andExpect(status().isNoContent());
    }

    @Test
    void alVencerElBloqueoEntraConLaClaveCorrectaYElContadorDeLaCuentaVuelveACero() throws Exception {
        crearUsuario(norte, "paciente@correo.com");
        for (int i = 0; i < FALLOS_POR_CUENTA; i++) {
            intentar(NORTE, "paciente@correo.com", MALA, "10.0.4.1");
        }
        intentar(NORTE, "paciente@correo.com", CLAVE, "10.0.4.1").andExpect(status().isTooManyRequests());

        // Simula que pasó el minuto de bloqueo (sin hacer esperar al test).
        enGimnasio(norte, () -> base.jdbc().update("UPDATE intento_ingreso SET bloqueado_hasta_ms = 0 WHERE clave_hash IN (?, ?)",
                sha256("paciente@correo.com"), sha256("10.0.4.1")));

        intentar(NORTE, "paciente@correo.com", CLAVE, "10.0.4.1").andExpect(status().isNoContent());
        assertThat(filasDe(norte, "CUENTA", "paciente@correo.com")).as("el éxito borra el contador de la cuenta").isZero();
        assertThat(filasDe(norte, "IP", "10.0.4.1")).as("pero no el de la IP").isEqualTo(1);
    }

    @Test
    void losContadoresSonDeCadaGimnasio() throws Exception {
        crearUsuario(norte, "doble@correo.com");
        crearUsuario(sur, "doble@correo.com");
        for (int i = 0; i < FALLOS_POR_CUENTA; i++) {
            intentar(NORTE, "doble@correo.com", MALA, "10.0.5.1");
        }
        intentar(NORTE, "doble@correo.com", CLAVE, "10.0.5.1").andExpect(status().isTooManyRequests());

        // Otro gimnasio, otra base: el mismo correo y la misma IP no están bloqueados ahí.
        intentar(SUR, "doble@correo.com", CLAVE, "10.0.5.1").andExpect(status().isNoContent());
    }

    @Test
    void noSeGuardanCorreosNiIpsEnClaro() throws Exception {
        intentar(NORTE, "privado@correo.com", MALA, "10.0.6.1").andExpect(status().isUnauthorized());

        List<String> claves = enGimnasio(norte, () -> base.jdbc().queryForList(
                "SELECT clave_hash FROM intento_ingreso", String.class));
        assertThat(claves).isNotEmpty().allSatisfy(clave -> assertThat(clave).matches("[0-9a-f]{64}"));
        assertThat(claves).contains(sha256("privado@correo.com"), sha256("10.0.6.1"));
    }

    @Test
    void alIngresarLaSesionGuardaLaFechaDeIngresoParaElVencimientoAbsoluto() throws Exception {
        crearUsuario(norte, "reloj@correo.com");
        intentar(NORTE, "reloj@correo.com", CLAVE, "10.0.7.1").andExpect(status().isNoContent());

        Integer conFecha = enGimnasio(norte, () -> base.jdbc().queryForObject(
                "SELECT COUNT(*) FROM sesion_ATTRIBUTES WHERE ATTRIBUTE_NAME = 'gym.ingresoEnMs'", Integer.class));
        assertThat(conFecha).isGreaterThanOrEqualTo(1);
    }

    // ------------------------------------------------------------- utilidades

    private ResultActions intentar(String host, String email, String clave, String ip) throws Exception {
        Cookie csrf = mvc.perform(get("http://" + host + "/api/sesion/csrf"))
                .andReturn().getResponse().getCookie("__Host-XSRF-TOKEN");
        return mvc.perform(post("http://" + host + "/api/sesion/ingresar")
                .with(pedido -> {
                    pedido.setRemoteAddr(ip);
                    return pedido;
                })
                .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"clave\":\"" + clave + "\"}"));
    }

    private void crearUsuario(GimnasioActual gimnasio, String email) throws Exception {
        enGimnasio(gimnasio, () -> {
            usuarios.crearActivoSiNoExiste(email, "Usuario de prueba", CLAVE);
            return null;
        });
    }

    private int filasDe(GimnasioActual gimnasio, String tipo, String valor) throws Exception {
        return enGimnasio(gimnasio, () -> base.jdbc().queryForObject(
                "SELECT COUNT(*) FROM intento_ingreso WHERE tipo = ? AND clave_hash = ?", Integer.class, tipo, sha256(valor)));
    }

    /** Mismo cálculo que LimitadorDeIntentos, repetido a propósito: si alguien lo cambia, este test avisa. */
    private static String sha256(String valor) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
    }

    private GimnasioActual alta(String slug, String nombre, String host) {
        ResultadoAlta resultado = aprovisionamiento.darDeAlta(new AltaGimnasio(slug, nombre, List.of(host)));
        return new GimnasioActual(resultado.gimnasioId(), slug, nombre, ZoneId.of("UTC"));
    }

    private static <T> T enGimnasio(GimnasioActual gimnasio, java.util.concurrent.Callable<T> operacion) throws Exception {
        return ContextoGimnasio.ejecutarPara(gimnasio, operacion);
    }
}
