package com.gimnasio.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.gimnasio.identidad.codigo.ServicioCodigoDeIngreso;
import com.gimnasio.notificaciones.Correo;
import com.gimnasio.plataforma.BaseDelGimnasio;
import com.gimnasio.plataforma.ContextoGimnasio;
import com.gimnasio.plataforma.GimnasioActual;
import com.gimnasio.plataforma.aprovisionamiento.AltaGimnasio;
import com.gimnasio.plataforma.aprovisionamiento.ResultadoAlta;
import com.gimnasio.plataforma.aprovisionamiento.ServicioAprovisionamiento;
import com.gimnasio.soporte.CorreosEnMemoria;
import com.gimnasio.soporte.MysqlPlataformaContenedor;

import jakarta.servlet.http.Cookie;

/** ADR-0006: ingreso con código por correo, de punta a punta con MySQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CorreosEnMemoria.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CodigoPorCorreoIT extends MysqlPlataformaContenedor {

    private static final String NORTE = "cod-norte.localhost";
    private static final String SUR = "cod-sur.localhost";
    private static final Pattern SEIS_DIGITOS = Pattern.compile("\\b(\\d{6})\\b");
    private static final AtomicInteger IPS = new AtomicInteger(1);

    @Autowired MockMvc mvc;
    @Autowired ServicioAprovisionamiento aprovisionamiento;
    @Autowired ServicioUsuarios usuarios;
    @Autowired ServicioCodigoDeIngreso codigos;
    @Autowired BaseDelGimnasio base;

    private GimnasioActual norte;
    private GimnasioActual sur;

    @BeforeAll
    void gimnasios() {
        norte = alta("cod-norte", "Cod Norte", NORTE);
        sur = alta("cod-sur", "Cod Sur", SUR);
    }

    @Test
    void alPedirloLeLlegaUnCorreoConElCodigoYElNombreDelGimnasio() throws Exception {
        crearUsuario(norte, "ana@correo.com", null);

        solicitar(NORTE, "ana@correo.com").andExpect(status().isAccepted());

        Correo correo = CorreosEnMemoria.ultimoPara("ana@correo.com").orElseThrow();
        assertThat(correo.asunto()).matches("\\d{6} es tu código para ingresar a Cod Norte");
        assertThat(correo.texto()).contains(codigoDe(correo)).contains("10 minutos");
    }

    @Test
    void laRespuestaEsIgualExistaONoElCorreoYSoloSeEnviaSiExiste() throws Exception {
        crearUsuario(norte, "existe@correo.com", null);

        String existe = solicitar(NORTE, "existe@correo.com").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String noExiste = solicitar(NORTE, "fantasma@correo.com").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(noExiste).isEqualTo(existe);
        assertThat(CorreosEnMemoria.para("fantasma@correo.com")).isEmpty();
    }

    @Test
    void conElCodigoCorrectoEntraYElMismoCodigoNoSirveDosVeces() throws Exception {
        crearUsuario(norte, "bruno@correo.com", null);
        solicitar(NORTE, "bruno@correo.com");
        String codigo = codigoDe(CorreosEnMemoria.ultimoPara("bruno@correo.com").orElseThrow());

        MvcResult adentro = ingresar(NORTE, "bruno@correo.com", codigo, nuevaIp()).andExpect(status().isNoContent()).andReturn();
        mvc.perform(get("http://" + NORTE + "/api/sesion/yo").cookie(adentro.getResponse().getCookie("__Host-SESION")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("bruno@correo.com"));

        ingresar(NORTE, "bruno@correo.com", codigo, nuevaIp()).andExpect(status().isUnauthorized());
    }

    @Test
    void unCodigoIncorrectoDa401() throws Exception {
        crearUsuario(norte, "caro@correo.com", null);
        solicitar(NORTE, "caro@correo.com");
        String codigo = codigoDe(CorreosEnMemoria.ultimoPara("caro@correo.com").orElseThrow());

        ingresar(NORTE, "caro@correo.com", otroCodigo(codigo), nuevaIp()).andExpect(status().isUnauthorized());
        ingresar(NORTE, "caro@correo.com", codigo, nuevaIp()).andExpect(status().isNoContent());
    }

    @Test
    void despuesDeCincoIntentosFallidosElCodigoQuedaAnulado() throws Exception {
        crearUsuario(norte, "dani@correo.com", null);
        solicitar(NORTE, "dani@correo.com");
        String codigo = codigoDe(CorreosEnMemoria.ultimoPara("dani@correo.com").orElseThrow());

        // Directo al servicio: por HTTP, el límite por cuenta (5 fallos → 429) se activaría antes.
        for (int i = 0; i < 5; i++) {
            assertThat(enGimnasio(norte, () -> codigos.verificar("dani@correo.com", otroCodigo(codigo)))).isFalse();
        }
        assertThat(enGimnasio(norte, () -> codigos.verificar("dani@correo.com", codigo))).isFalse();
    }

    @Test
    void conMuchosCodigosIncorrectosPorHttpLaCuentaSeBloquea() throws Exception {
        crearUsuario(norte, "eli@correo.com", null);
        solicitar(NORTE, "eli@correo.com");
        String codigo = codigoDe(CorreosEnMemoria.ultimoPara("eli@correo.com").orElseThrow());
        String ip = nuevaIp();
        for (int i = 0; i < 5; i++) {
            ingresar(NORTE, "eli@correo.com", otroCodigo(codigo), ip).andExpect(status().isUnauthorized());
        }

        ingresar(NORTE, "eli@correo.com", codigo, ip).andExpect(status().isTooManyRequests());
    }

    @Test
    void noSeMandaOtroAntesDeUnMinutoYPedirUnoNuevoAnulaElAnterior() throws Exception {
        crearUsuario(norte, "fede@correo.com", null);
        solicitar(NORTE, "fede@correo.com");
        solicitar(NORTE, "fede@correo.com").andExpect(status().isAccepted()); // misma respuesta, pero...
        assertThat(CorreosEnMemoria.para("fede@correo.com")).as("...no manda un segundo correo").hasSize(1);
        String viejo = codigoDe(CorreosEnMemoria.ultimoPara("fede@correo.com").orElseThrow());

        hacerPasarMinutos(norte, "fede@correo.com", 2);
        solicitar(NORTE, "fede@correo.com");
        String nuevo = codigoDe(CorreosEnMemoria.ultimoPara("fede@correo.com").orElseThrow());

        assertThat(CorreosEnMemoria.para("fede@correo.com")).hasSize(2);
        if (!viejo.equals(nuevo)) { // 1 en un millón de que salga el mismo número
            assertThat(enGimnasio(norte, () -> codigos.verificar("fede@correo.com", viejo))).isFalse();
        }
        assertThat(enGimnasio(norte, () -> codigos.verificar("fede@correo.com", nuevo))).isTrue();
    }

    @Test
    void comoMaximoCincoCorreosPorHora() throws Exception {
        crearUsuario(norte, "gabi@correo.com", null);
        for (int i = 0; i < 7; i++) {
            solicitar(NORTE, "gabi@correo.com").andExpect(status().isAccepted());
            hacerPasarMinutos(norte, "gabi@correo.com", 2);
        }

        assertThat(CorreosEnMemoria.para("gabi@correo.com")).hasSize(5);
    }

    @Test
    void unCodigoVencidoNoSirve() throws Exception {
        crearUsuario(norte, "hugo@correo.com", null);
        solicitar(NORTE, "hugo@correo.com");
        String codigo = codigoDe(CorreosEnMemoria.ultimoPara("hugo@correo.com").orElseThrow());
        enGimnasio(norte, () -> base.jdbc().update("""
                UPDATE codigo_ingreso c JOIN usuario u ON u.id = c.usuario_id SET c.expira_ms = c.expira_ms - 600001
                WHERE u.email = 'hugo@correo.com'
                """));

        ingresar(NORTE, "hugo@correo.com", codigo, nuevaIp()).andExpect(status().isUnauthorized());
    }

    @Test
    void unUsuarioSuspendidoNoRecibeCodigo() throws Exception {
        crearUsuario(norte, "ines@correo.com", null);
        enGimnasio(norte, () -> base.jdbc().update("UPDATE usuario SET estado = 'SUSPENDIDO' WHERE email = 'ines@correo.com'"));

        solicitar(NORTE, "ines@correo.com").andExpect(status().isAccepted());

        assertThat(CorreosEnMemoria.para("ines@correo.com")).isEmpty();
    }

    @Test
    void conUnRolConMfaElCodigoEsSoloElPrimerPaso() throws Exception {
        crearUsuario(norte, "juli@correo.com", "RECEPCION");
        solicitar(NORTE, "juli@correo.com");
        String codigo = codigoDe(CorreosEnMemoria.ultimoPara("juli@correo.com").orElseThrow());

        MvcResult paso1 = ingresar(NORTE, "juli@correo.com", codigo, nuevaIp())
                .andExpect(status().isOk()).andExpect(jsonPath("$.paso").value("CONFIGURAR_MFA")).andReturn();

        mvc.perform(get("http://" + NORTE + "/api/sesion/yo").cookie(paso1.getResponse().getCookie("__Host-SESION")))
                .andExpect(status().isUnauthorized()); // el correo solo no alcanza para un rol con MFA
    }

    @Test
    void elCodigoDeUnGimnasioNoSirveEnOtro() throws Exception {
        crearUsuario(norte, "kai@correo.com", null);
        crearUsuario(sur, "kai@correo.com", null);
        solicitar(NORTE, "kai@correo.com");
        String codigoNorte = codigoDe(CorreosEnMemoria.ultimoPara("kai@correo.com").orElseThrow());

        ingresar(SUR, "kai@correo.com", codigoNorte, nuevaIp()).andExpect(status().isUnauthorized());
    }

    @Test
    void elCodigoNoSeGuardaEnClaro() throws Exception {
        crearUsuario(norte, "lu@correo.com", null);
        solicitar(NORTE, "lu@correo.com");
        String codigo = codigoDe(CorreosEnMemoria.ultimoPara("lu@correo.com").orElseThrow());

        List<String> guardados = enGimnasio(norte, () -> base.jdbc().queryForList("""
                SELECT c.hash_codigo FROM codigo_ingreso c JOIN usuario u ON u.id = c.usuario_id WHERE u.email = 'lu@correo.com'
                """, String.class));
        assertThat(guardados).singleElement().satisfies(hash -> {
            assertThat(hash).matches("[0-9a-f]{64}");
            assertThat(hash).doesNotContain(codigo);
        });
    }

    @Test
    void datosIncompletosDan400() throws Exception {
        solicitar(NORTE, "no-es-un-correo").andExpect(status().isBadRequest());
        ingresar(NORTE, "ana@correo.com", "", nuevaIp()).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------- utilidades

    private ResultActions solicitar(String host, String email) throws Exception {
        Cookie csrf = cookieCsrf(host);
        return mvc.perform(post("http://" + host + "/api/sesion/codigo/solicitar")
                .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions ingresar(String host, String email, String codigo, String ip) throws Exception {
        Cookie csrf = cookieCsrf(host);
        return mvc.perform(post("http://" + host + "/api/sesion/codigo/ingresar")
                .with(pedido -> {
                    pedido.setRemoteAddr(ip);
                    return pedido;
                })
                .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"codigo\":\"" + codigo + "\"}"));
    }

    private Cookie cookieCsrf(String host) throws Exception {
        return mvc.perform(get("http://" + host + "/api/sesion/csrf")).andReturn().getResponse().getCookie("__Host-XSRF-TOKEN");
    }

    private static String codigoDe(Correo correo) {
        Matcher m = SEIS_DIGITOS.matcher(correo.asunto());
        assertThat(m.find()).as("el asunto trae el código").isTrue();
        return m.group(1);
    }

    /** Un código de 6 dígitos distinto del correcto. */
    private static String otroCodigo(String codigo) {
        return String.format("%06d", (Integer.parseInt(codigo) + 1) % 1_000_000);
    }

    /** Mueve hacia atrás la fecha de los códigos del usuario: simula que pasaron N minutos. */
    private void hacerPasarMinutos(GimnasioActual gimnasio, String email, int minutos) throws Exception {
        long ms = minutos * 60_000L;
        enGimnasio(gimnasio, () -> base.jdbc().update("""
                UPDATE codigo_ingreso c JOIN usuario u ON u.id = c.usuario_id
                SET c.creado_ms = c.creado_ms - ?, c.expira_ms = c.expira_ms - ? WHERE u.email = ?
                """, ms, ms, email));
    }

    private static String nuevaIp() {
        return "10.2." + (IPS.get() / 250) + "." + (IPS.getAndIncrement() % 250 + 1);
    }

    private void crearUsuario(GimnasioActual gimnasio, String email, String rol) throws Exception {
        enGimnasio(gimnasio, () -> {
            usuarios.crearActivoSiNoExiste(email, "Usuario de prueba", "clave-larga-de-prueba-123");
            if (rol != null) {
                usuarios.asignarRol(email, rol);
            }
            return null;
        });
    }

    private GimnasioActual alta(String slug, String nombre, String host) {
        ResultadoAlta resultado = aprovisionamiento.darDeAlta(new AltaGimnasio(slug, nombre, List.of(host)));
        return new GimnasioActual(resultado.gimnasioId(), slug, nombre, ZoneId.of("UTC"));
    }

    private static <T> T enGimnasio(GimnasioActual gimnasio, Callable<T> operacion) throws Exception {
        return ContextoGimnasio.ejecutarPara(gimnasio, operacion);
    }
}
