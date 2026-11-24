package com.gimnasio.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

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

import com.gimnasio.identidad.mfa.ServicioMfa;
import com.gimnasio.identidad.mfa.Totp;
import com.gimnasio.plataforma.BaseDelGimnasio;
import com.gimnasio.plataforma.ContextoGimnasio;
import com.gimnasio.plataforma.GimnasioActual;
import com.gimnasio.plataforma.aprovisionamiento.AltaGimnasio;
import com.gimnasio.plataforma.aprovisionamiento.ResultadoAlta;
import com.gimnasio.plataforma.aprovisionamiento.ServicioAprovisionamiento;
import com.gimnasio.soporte.MysqlPlataformaContenedor;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Paso 0.6b parte 2 (ADR-0005): roles y MFA por rol, de punta a punta con MySQL real.
 *
 * <p>Cada test crea sus propios usuarios, roles e IPs: no depende del orden ni de otros tests.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MfaIT extends MysqlPlataformaContenedor {

    private static final String CLAVE = "clave-larga-de-prueba-123";
    private static final String NORTE = "mfa-norte.localhost";
    private static final String SUR = "mfa-sur.localhost";
    private static final AtomicInteger IPS = new AtomicInteger(1);

    @Autowired MockMvc mvc;
    @Autowired ServicioAprovisionamiento aprovisionamiento;
    @Autowired ServicioUsuarios usuarios;
    @Autowired ServicioRoles roles;
    @Autowired ServicioMfa mfa;
    @Autowired BaseDelGimnasio base;

    private GimnasioActual norte;
    private GimnasioActual sur;

    @BeforeAll
    void gimnasios() {
        norte = alta("mfa-norte", "MFA Norte", NORTE);
        sur = alta("mfa-sur", "MFA Sur", SUR);
    }

    // ------------------------------------------------------------------ roles

    @Test
    void cadaGimnasioArrancaConLosCuatroRolesYSuMfaInicial() throws Exception {
        List<Rol> iniciales = enGimnasio(sur, roles::listar);

        assertThat(iniciales).extracting(Rol::codigo).startsWith("ADMINISTRADOR", "RECEPCION", "PROFESOR", "ALUMNO");
        assertThat(iniciales).filteredOn(Rol::mfa).extracting(Rol::codigo).containsExactly("ADMINISTRADOR", "RECEPCION");
    }

    @Test
    void unUsuarioPuedeTenerVariosRolesYBastaUnoConMfaParaExigirlo() throws Exception {
        crearUsuario(norte, "profe-recepcion@correo.com", "PROFESOR");
        enGimnasio(norte, () -> usuarios.asignarRol("profe-recepcion@correo.com", "RECEPCION"));
        enGimnasio(norte, () -> usuarios.asignarRol("profe-recepcion@correo.com", "RECEPCION")); // idempotente

        assertThat(enGimnasio(norte, () -> usuarios.rolesDe("profe-recepcion@correo.com")))
                .containsExactly("PROFESOR", "RECEPCION");
        ingresar(NORTE, "profe-recepcion@correo.com", nuevaIp())
                .andExpect(status().isOk()).andExpect(jsonPath("$.paso").value("CONFIGURAR_MFA"));
    }

    @Test
    void unCodigoDeRolInvalidoSeRechaza() {
        assertThatThrownBy(() -> enGimnasio(norte, () -> roles.crear("nutricionista", "Nutri", false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> enGimnasio(norte, () -> usuarios.asignarRol("nadie@correo.com", "PROFESOR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cambiarElMfaDeUnRolAplicaEnElProximoIngreso() throws Exception {
        enGimnasio(norte, () -> roles.crear("NUTRICIONISTA", "Nutricionista", false));
        crearUsuario(norte, "nutri@correo.com", "NUTRICIONISTA");
        ingresar(NORTE, "nutri@correo.com", nuevaIp()).andExpect(status().isNoContent());

        assertThat(enGimnasio(norte, () -> roles.cambiarMfa("NUTRICIONISTA", true))).isTrue();

        ingresar(NORTE, "nutri@correo.com", nuevaIp())
                .andExpect(status().isOk()).andExpect(jsonPath("$.paso").value("CONFIGURAR_MFA"));
        assertThat(enGimnasio(norte, () -> roles.cambiarMfa("NO_EXISTE", true))).isFalse();
    }

    // ------------------------------------------------------- sin MFA en sus roles

    @Test
    void sinRolesConMfaEntraSoloConLaClave() throws Exception {
        crearUsuario(norte, "alumna@correo.com", "ALUMNO");
        Cookie sesion = sesionDe(ingresar(NORTE, "alumna@correo.com", nuevaIp()).andExpect(status().isNoContent()).andReturn());

        mvc.perform(get("http://" + NORTE + "/api/sesion/yo").cookie(sesion)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------- configuración

    @Test
    void laPrimeraVezObligaAConfigurarYRecienAlConfirmarQuedaAutenticado() throws Exception {
        crearUsuario(norte, "admin1@correo.com", "ADMINISTRADOR");
        String ip = nuevaIp();
        MvcResult paso1 = ingresar(NORTE, "admin1@correo.com", ip)
                .andExpect(status().isOk()).andExpect(jsonPath("$.paso").value("CONFIGURAR_MFA")).andReturn();
        Cookie pendiente = sesionDe(paso1);

        mvc.perform(get("http://" + NORTE + "/api/sesion/yo").cookie(pendiente))
                .andExpect(status().isUnauthorized()); // pasó la clave, pero NO está autenticado

        String configuracion = postMfa(NORTE, "configurar", pendiente, null, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secreto = JsonPath.read(configuracion, "$.secreto");
        assertThat((String) JsonPath.read(configuracion, "$.uri")).startsWith("otpauth://totp/MFA%20Norte:admin1%40correo.com?");

        MvcResult confirmado = postMfa(NORTE, "confirmar", pendiente, codigoActual(Totp.desdeBase32(secreto)), ip)
                .andExpect(status().isOk()).andReturn();
        List<String> codigos = JsonPath.read(confirmado.getResponse().getContentAsString(), "$.codigosRecuperacion");
        assertThat(codigos).hasSize(10).allSatisfy(c -> assertThat(c).matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));

        mvc.perform(get("http://" + NORTE + "/api/sesion/yo").cookie(sesionDe(confirmado))).andExpect(status().isOk());
    }

    @Test
    void unCodigoIncorrectoAlConfirmarNoActivaNada() throws Exception {
        crearUsuario(norte, "admin2@correo.com", "ADMINISTRADOR");
        String ip = nuevaIp();
        Cookie pendiente = sesionDe(ingresar(NORTE, "admin2@correo.com", ip).andReturn());
        postMfa(NORTE, "configurar", pendiente, null, ip).andExpect(status().isOk());

        postMfa(NORTE, "confirmar", pendiente, "000000", ip).andExpect(status().isUnauthorized());

        assertThat(enGimnasio(norte, () -> mfa.requisitoPara("admin2@correo.com")).name()).isEqualTo("CONFIGURAR");
    }

    @Test
    void elSecretoSeGuardaCifradoYNoSePuedePisarUnoConfirmado() throws Exception {
        UsuarioConMfa ana = configurarMfa(NORTE, "admin3@correo.com");

        byte[] guardado = enGimnasio(norte, () -> base.jdbc().queryForObject("""
                SELECT m.secreto_cifrado FROM usuario_mfa m JOIN usuario u ON u.id = m.usuario_id WHERE u.email = ?
                """, byte[].class, "admin3@correo.com"));
        assertThat(new String(guardado, StandardCharsets.ISO_8859_1)).doesNotContain(Totp.base32(ana.secreto()));
        assertThatThrownBy(() -> enGimnasio(norte, () -> mfa.iniciarConfiguracion("admin3@correo.com")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------- código

    @Test
    void conMfaConfiguradoPideElCodigoYConElCorrectoEntra() throws Exception {
        UsuarioConMfa bruno = configurarMfa(NORTE, "admin4@correo.com");
        String ip = nuevaIp();
        MvcResult paso1 = ingresar(NORTE, "admin4@correo.com", ip)
                .andExpect(status().isOk()).andExpect(jsonPath("$.paso").value("CODIGO_MFA")).andReturn();
        Cookie pendiente = sesionDe(paso1);
        mvc.perform(get("http://" + NORTE + "/api/sesion/yo").cookie(pendiente)).andExpect(status().isUnauthorized());

        postMfa(NORTE, "verificar", pendiente, "000000", ip).andExpect(status().isUnauthorized());
        MvcResult ok = postMfa(NORTE, "verificar", pendiente, codigoSiguiente(bruno.secreto()), ip)
                .andExpect(status().isNoContent()).andReturn();

        mvc.perform(get("http://" + NORTE + "/api/sesion/yo").cookie(sesionDe(ok))).andExpect(status().isOk());
    }

    @Test
    void unCodigoYaUsadoNoSirveOtraVez() throws Exception {
        UsuarioConMfa caro = configurarMfa(NORTE, "admin5@correo.com");
        String codigo = codigoSiguiente(caro.secreto());
        String ip = nuevaIp();
        postMfa(NORTE, "verificar", sesionDe(ingresar(NORTE, "admin5@correo.com", ip).andReturn()), codigo, ip)
                .andExpect(status().isNoContent());

        // Alguien lo vio por encima del hombro e intenta usarlo en los mismos segundos:
        postMfa(NORTE, "verificar", sesionDe(ingresar(NORTE, "admin5@correo.com", ip).andReturn()), codigo, ip)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cadaCodigoDeRecuperacionSirveUnaSolaVez() throws Exception {
        UsuarioConMfa dani = configurarMfa(NORTE, "admin6@correo.com");
        String ip = nuevaIp();
        String primero = dani.codigosRecuperacion().get(0);

        postMfa(NORTE, "verificar", sesionDe(ingresar(NORTE, "admin6@correo.com", ip).andReturn()), primero, ip)
                .andExpect(status().isNoContent());
        postMfa(NORTE, "verificar", sesionDe(ingresar(NORTE, "admin6@correo.com", ip).andReturn()), primero, ip)
                .andExpect(status().isUnauthorized());
        // En minúsculas y sin guion también vale (se normaliza):
        String segundo = dani.codigosRecuperacion().get(1).replace("-", "").toLowerCase();
        postMfa(NORTE, "verificar", sesionDe(ingresar(NORTE, "admin6@correo.com", ip).andReturn()), segundo, ip)
                .andExpect(status().isNoContent());

        assertThat(enGimnasio(norte, () -> mfa.codigosDeRecuperacionRestantes("admin6@correo.com"))).isEqualTo(8);
    }

    @Test
    void losCodigosIncorrectosCuentanParaElLimiteDeIntentos() throws Exception {
        configurarMfa(NORTE, "admin7@correo.com");
        String ip = nuevaIp();
        Cookie pendiente = sesionDe(ingresar(NORTE, "admin7@correo.com", ip).andReturn());
        for (int i = 0; i < 5; i++) {
            postMfa(NORTE, "verificar", pendiente, "00000" + i, ip).andExpect(status().isUnauthorized());
        }

        postMfa(NORTE, "verificar", pendiente, "123456", ip).andExpect(status().isTooManyRequests());
    }

    // ------------------------------------------------- sin paso previo / otro gimnasio

    @Test
    void sinHaberPasadoLaClaveLosEndpointsDeMfaDan401() throws Exception {
        String ip = nuevaIp();
        postMfa(NORTE, "verificar", null, "123456", ip).andExpect(status().isUnauthorized());
        postMfa(NORTE, "configurar", null, null, ip).andExpect(status().isUnauthorized());
        postMfa(NORTE, "confirmar", null, "123456", ip).andExpect(status().isUnauthorized());
    }

    @Test
    void conMfaConfiguradoNoSePuedeIrAlPasoDeConfiguracion() throws Exception {
        configurarMfa(NORTE, "admin8@correo.com");
        String ip = nuevaIp();
        Cookie pendiente = sesionDe(ingresar(NORTE, "admin8@correo.com", ip).andReturn());

        // Quien robó la clave no puede "reconfigurar" el MFA con su propio celular.
        postMfa(NORTE, "configurar", pendiente, null, ip).andExpect(status().isUnauthorized());
    }

    @Test
    void elIngresoPendienteDeUnGimnasioNoSirveEnOtro() throws Exception {
        UsuarioConMfa eli = configurarMfa(NORTE, "admin9@correo.com");
        String ip = nuevaIp();
        Cookie pendienteNorte = sesionDe(ingresar(NORTE, "admin9@correo.com", ip).andReturn());

        postMfa(SUR, "verificar", pendienteNorte, codigoSiguiente(eli.secreto()), ip).andExpect(status().isUnauthorized());
    }

    @Test
    void unUsuarioSuspendidoEntreLaClaveYElCodigoNoEntra() throws Exception {
        UsuarioConMfa fede = configurarMfa(NORTE, "admin10@correo.com");
        String ip = nuevaIp();
        Cookie pendiente = sesionDe(ingresar(NORTE, "admin10@correo.com", ip).andReturn());
        enGimnasio(norte, () -> base.jdbc().update("UPDATE usuario SET estado = 'SUSPENDIDO' WHERE email = 'admin10@correo.com'"));

        postMfa(NORTE, "verificar", pendiente, codigoSiguiente(fede.secreto()), ip).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- utilidades

    private record UsuarioConMfa(byte[] secreto, List<String> codigosRecuperacion) {
    }

    /** Crea un administrador y le configura el MFA por HTTP, como lo haría un usuario real. */
    private UsuarioConMfa configurarMfa(String host, String email) throws Exception {
        crearUsuario(host.equals(NORTE) ? norte : sur, email, "ADMINISTRADOR");
        String ip = nuevaIp();
        Cookie pendiente = sesionDe(ingresar(host, email, ip).andExpect(jsonPath("$.paso").value("CONFIGURAR_MFA")).andReturn());
        byte[] secreto = Totp.desdeBase32(JsonPath.read(postMfa(host, "configurar", pendiente, null, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.secreto"));
        List<String> codigos = JsonPath.read(postMfa(host, "confirmar", pendiente, codigoActual(secreto), ip)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.codigosRecuperacion");
        return new UsuarioConMfa(secreto, codigos);
    }

    /** El código de AHORA (lo que mostraría la app). */
    private static String codigoActual(byte[] secreto) {
        return Totp.codigo(secreto, Totp.paso(System.currentTimeMillis()), 6);
    }

    /**
     * El código del PRÓXIMO intervalo de 30 s: se acepta por la tolerancia de ±1 y siempre es posterior al
     * usado al configurar, así el test no depende de esperar 30 segundos.
     */
    private static String codigoSiguiente(byte[] secreto) {
        return Totp.codigo(secreto, Totp.paso(System.currentTimeMillis()) + 1, 6);
    }

    private ResultActions ingresar(String host, String email, String ip) throws Exception {
        Cookie csrf = cookieCsrf(host);
        return mvc.perform(post("http://" + host + "/api/sesion/ingresar")
                .with(pedido -> {
                    pedido.setRemoteAddr(ip);
                    return pedido;
                })
                .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"clave\":\"" + CLAVE + "\"}"));
    }

    private ResultActions postMfa(String host, String accion, Cookie sesion, String codigo, String ip) throws Exception {
        Cookie csrf = cookieCsrf(host);
        var pedido = post("http://" + host + "/api/sesion/mfa/" + accion)
                .with(p -> {
                    p.setRemoteAddr(ip);
                    return p;
                })
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(codigo == null ? "{}" : "{\"codigo\":\"" + codigo + "\"}");
        pedido = sesion == null ? pedido.cookie(csrf) : pedido.cookie(csrf, sesion);
        return mvc.perform(pedido);
    }

    private Cookie cookieCsrf(String host) throws Exception {
        return mvc.perform(get("http://" + host + "/api/sesion/csrf")).andReturn().getResponse().getCookie("__Host-XSRF-TOKEN");
    }

    private static Cookie sesionDe(MvcResult resultado) {
        Cookie sesion = resultado.getResponse().getCookie("__Host-SESION");
        assertThat(sesion).as("cookie de sesión; Set-Cookie: %s", resultado.getResponse().getHeaders("Set-Cookie")).isNotNull();
        return sesion;
    }

    private static String nuevaIp() {
        return "10.1." + (IPS.get() / 250) + "." + (IPS.getAndIncrement() % 250 + 1);
    }

    private void crearUsuario(GimnasioActual gimnasio, String email, String rol) throws Exception {
        enGimnasio(gimnasio, () -> {
            usuarios.crearActivoSiNoExiste(email, "Usuario de prueba", CLAVE);
            usuarios.asignarRol(email, rol);
        });
    }

    private GimnasioActual alta(String slug, String nombre, String host) {
        ResultadoAlta resultado = aprovisionamiento.darDeAlta(new AltaGimnasio(slug, nombre, List.of(host)));
        return new GimnasioActual(resultado.gimnasioId(), slug, nombre, ZoneId.of("UTC"));
    }

    private static <T> T enGimnasio(GimnasioActual gimnasio, Callable<T> operacion) throws Exception {
        return ContextoGimnasio.ejecutarPara(gimnasio, operacion);
    }

    private static void enGimnasio(GimnasioActual gimnasio, Runnable operacion) throws Exception {
        ContextoGimnasio.ejecutarPara(gimnasio, () -> {
            operacion.run();
            return null;
        });
    }
}
