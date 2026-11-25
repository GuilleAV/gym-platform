package com.gimnasio.inscripcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gimnasio.identidad.ServicioRoles;
import com.gimnasio.identidad.ServicioUsuarios;
import com.gimnasio.plataforma.BaseDelGimnasio;
import com.gimnasio.plataforma.ContextoGimnasio;
import com.gimnasio.plataforma.GimnasioActual;
import com.gimnasio.plataforma.aprovisionamiento.AltaGimnasio;
import com.gimnasio.plataforma.aprovisionamiento.ResultadoAlta;
import com.gimnasio.plataforma.aprovisionamiento.ServicioAprovisionamiento;
import com.gimnasio.soporte.CorreosEnMemoria;
import com.gimnasio.soporte.MysqlPlataformaContenedor;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** ADR-0007, UC-05/UC-06: completar la ficha por invitación y en el mostrador, con MySQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CorreosEnMemoria.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FichaIT extends MysqlPlataformaContenedor {

    private static final String CLAVE = "clave-larga-de-prueba-123";
    private static final String SIMPLE = "fic-simple.localhost";
    private static final String COMPLETO = "fic-completo.localhost";
    private static final Pattern TOKEN = Pattern.compile("/invitacion#t=([A-Za-z0-9_-]{43})");
    private static final String DATOS_ADULTO = """
            {"dni":"30123456","direccion":"Av. Siempre Viva 742","emergenciaNombre":"Marta Pérez",
             "emergenciaTelefono":"1155550000","objetivo":"FUERZA"}""";
    private static final String DOCS_ADULTO = "[{\"codigo\":\"DESLINDE\",\"version\":1},{\"codigo\":\"PRIVACIDAD\",\"version\":1}]";

    @Autowired MockMvc mvc;
    @Autowired ServicioAprovisionamiento aprovisionamiento;
    @Autowired ServicioUsuarios usuarios;
    @Autowired ServicioRoles roles;
    @Autowired BaseDelGimnasio base;

    private GimnasioActual simple;
    private GimnasioActual completo;
    private Cookie recepcionSimple;
    private Cookie recepcionCompleto;
    private Cookie alumnoSimple;

    @BeforeAll
    void preparar() throws Exception {
        simple = alta("fic-simple", "Fic Simple", SIMPLE);
        completo = alta("fic-completo", "Fic Completo", COMPLETO);
        enGimnasio(completo, () -> base.jdbc().update("UPDATE configuracion_gimnasio SET valor = 'COMPLETO' WHERE clave = 'inscripcion.modo'"));
        for (GimnasioActual g : List.of(simple, completo)) {
            enGimnasio(g, () -> roles.cambiarMfa("RECEPCION", false));
        }
        recepcionSimple = sesionConClave(simple, SIMPLE, "recepcion@fic.com", "RECEPCION");
        recepcionCompleto = sesionConClave(completo, COMPLETO, "recepcion@fic.com", "RECEPCION");
        alumnoSimple = sesionConClave(simple, SIMPLE, "alumno@fic.com", "ALUMNO");
    }

    // ---------------------------------------------------------- abrir la invitación

    @Test
    void conUnaInvitacionValidaSeVeLaFichaConLosDocumentosAAceptar() throws Exception {
        String token = inscribirConCorreo(SIMPLE, recepcionSimple, "ana@fic.com", "1990-05-10");

        abrir(SIMPLE, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre"))
                .andExpect(jsonPath("$.email").value("ana@fic.com"))
                .andExpect(jsonPath("$.esMenor").value(false))
                .andExpect(jsonPath("$.documentos.length()").value(2))
                .andExpect(jsonPath("$.documentos[0].codigo").value("DESLINDE"));
    }

    @Test
    void unTokenInventadoOMalFormadoDa404ConElMismoMensaje() throws Exception {
        String inventado = abrir(SIMPLE, "A".repeat(43)).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String malFormado = abrir(SIMPLE, "corto").andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(malFormado).isEqualTo(inventado);
    }

    @Test
    void unaInvitacionVencidaNoSirve() throws Exception {
        String token = inscribirConCorreo(SIMPLE, recepcionSimple, "vencida@fic.com", "1990-05-10");
        enGimnasio(simple, () -> base.jdbc().update("UPDATE invitacion SET expira_ms = 1 WHERE usada_ms IS NULL AND inscripcion_id = "
                + "(SELECT i.id FROM inscripcion i JOIN alumno a ON a.id = i.alumno_id WHERE a.email = 'vencida@fic.com')"));

        abrir(SIMPLE, token).andExpect(status().isNotFound());
    }

    @Test
    void unaInvitacionDeUnGimnasioNoSirveEnOtro() throws Exception {
        String token = inscribirConCorreo(SIMPLE, recepcionSimple, "cruzada@fic.com", "1990-05-10");
        abrir(COMPLETO, token).andExpect(status().isNotFound());
    }

    @Test
    void sinAbrirLaInvitacionNoSeVeNingunaFicha() throws Exception {
        mvc.perform(get("http://" + SIMPLE + "/api/ficha")).andExpect(status().isUnauthorized());
    }

    @Test
    void laSesionDeFichaNoDaAccesoAlRestoDelSistema() throws Exception {
        Cookie ficha = sesionDe(abrir(SIMPLE, inscribirConCorreo(SIMPLE, recepcionSimple, "curiosa@fic.com", "1990-05-10")));

        mvc.perform(get("http://" + SIMPLE + "/api/sesion/yo").cookie(ficha)).andExpect(status().isUnauthorized());
        mvc.perform(get("http://" + SIMPLE + "/api/inscripciones").cookie(ficha)).andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------------------- borrador

    @Test
    void elBorradorSeGuardaYSeRecupera() throws Exception {
        Cookie ficha = sesionDe(abrir(SIMPLE, inscribirConCorreo(SIMPLE, recepcionSimple, "borrador@fic.com", "1990-05-10")));

        enviarJson(put("http://" + SIMPLE + "/api/ficha"), ficha, "{\"dni\":\"30123456\",\"objetivo\":\"salud\"}")
                .andExpect(status().isNoContent());

        mvc.perform(get("http://" + SIMPLE + "/api/ficha").cookie(ficha)).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.dni").value("30123456"))
                .andExpect(jsonPath("$.datos.objetivo").value("SALUD"));
    }

    @Test
    void unBorradorConDatosMalFormadosDa400() throws Exception {
        Cookie ficha = sesionDe(abrir(SIMPLE, inscribirConCorreo(SIMPLE, recepcionSimple, "basura@fic.com", "1990-05-10")));
        enviarJson(put("http://" + SIMPLE + "/api/ficha"), ficha, "{\"dni\":\"x\"}").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ envío

    @Test
    void enModoSimpleAlEnviarQuedaActivoConRolAlumnoYPuedeEntrarConCodigo() throws Exception {
        Cookie ficha = sesionDe(abrir(SIMPLE, inscribirConCorreo(SIMPLE, recepcionSimple, "lista@fic.com", "1990-05-10")));

        enviarFicha(SIMPLE, ficha, DATOS_ADULTO, DOCS_ADULTO).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACTIVO"));

        Map<String, Object> fila = enGimnasio(simple, () -> base.jdbc().queryForMap("""
                SELECT a.estado AS alumno, u.estado AS usuario, i.estado AS inscripcion,
                       (SELECT COUNT(*) FROM aceptacion_documento d WHERE d.alumno_id = a.id AND d.medio = 'INVITACION') AS aceptados,
                       (SELECT COUNT(*) FROM invitacion v WHERE v.inscripcion_id = i.id AND v.usada_ms IS NULL) AS vigentes
                FROM alumno a JOIN usuario u ON u.id = a.usuario_id JOIN inscripcion i ON i.alumno_id = a.id
                WHERE a.email = 'lista@fic.com'
                """));
        assertThat(fila).containsEntry("alumno", "ACTIVO").containsEntry("usuario", "ACTIVO").containsEntry("inscripcion", "APROBADA");
        assertThat(((Number) fila.get("aceptados")).intValue()).isEqualTo(2);
        assertThat(((Number) fila.get("vigentes")).intValue()).as("la invitación quedó usada").isZero();
        assertThat(enGimnasio(simple, () -> usuarios.rolesDe("lista@fic.com"))).containsExactly("ALUMNO");

        // Ya activo: el ingreso con código por correo le funciona.
        int antes = CorreosEnMemoria.para("lista@fic.com").size();
        Cookie csrf = cookieCsrf(SIMPLE);
        mvc.perform(post("http://" + SIMPLE + "/api/sesion/codigo/solicitar").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"lista@fic.com\"}")).andExpect(status().isAccepted());
        assertThat(CorreosEnMemoria.para("lista@fic.com")).hasSize(antes + 1);
    }

    @Test
    void despuesDeEnviarLaInvitacionYLaSesionDeFichaYaNoSirven() throws Exception {
        String token = inscribirConCorreo(SIMPLE, recepcionSimple, "usada@fic.com", "1990-05-10");
        Cookie ficha = sesionDe(abrir(SIMPLE, token));
        enviarFicha(SIMPLE, ficha, DATOS_ADULTO, DOCS_ADULTO).andExpect(status().isOk());

        abrir(SIMPLE, token).andExpect(status().isNotFound());
        mvc.perform(get("http://" + SIMPLE + "/api/ficha").cookie(ficha)).andExpect(status().isUnauthorized());
    }

    @Test
    void sinAceptarLosDocumentosOConDatosIncompletosNoSeEnvia() throws Exception {
        Cookie ficha = sesionDe(abrir(SIMPLE, inscribirConCorreo(SIMPLE, recepcionSimple, "incompleta@fic.com", "1990-05-10")));

        enviarFicha(SIMPLE, ficha, DATOS_ADULTO, "[{\"codigo\":\"DESLINDE\",\"version\":1}]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0]").value(org.hamcrest.Matchers.startsWith("documentos")));
        enviarFicha(SIMPLE, ficha, "{\"dni\":\"30123456\"}", DOCS_ADULTO)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.length()").value(4));
    }

    @Test
    void unDocumentoAceptadoEnUnaVersionViejaNoVale() throws Exception {
        Cookie ficha = sesionDe(abrir(SIMPLE, inscribirConCorreo(SIMPLE, recepcionSimple, "version@fic.com", "1990-05-10")));
        enviarFicha(SIMPLE, ficha, DATOS_ADULTO, "[{\"codigo\":\"DESLINDE\",\"version\":0},{\"codigo\":\"PRIVACIDAD\",\"version\":1}]")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unMenorNecesitaAlResponsableYLosDocumentosLosAceptaEl() throws Exception {
        String hace15 = LocalDate.now(ZoneId.of("UTC")).minusYears(15).toString();
        Cookie ficha = sesionDe(abrir(SIMPLE, inscribirConCorreo(SIMPLE, recepcionSimple, "menor@fic.com", hace15)));
        String docsMenor = "[{\"codigo\":\"DESLINDE\",\"version\":1},{\"codigo\":\"PRIVACIDAD\",\"version\":1},"
                + "{\"codigo\":\"CONSENTIMIENTO_MENOR\",\"version\":1}]";

        enviarFicha(SIMPLE, ficha, DATOS_ADULTO, docsMenor).andExpect(status().isBadRequest());
        String conResponsable = DATOS_ADULTO.replace("}", ",\"responsableNombre\":\"Laura Gómez\",\"responsableDni\":\"20111222\","
                + "\"responsableTelefono\":\"1144443333\",\"responsableVinculo\":\"MADRE\"}");
        enviarFicha(SIMPLE, ficha, conResponsable, docsMenor).andExpect(status().isOk());

        List<String> aceptadoPor = enGimnasio(simple, () -> base.jdbc().queryForList("""
                SELECT DISTINCT d.aceptado_por FROM aceptacion_documento d JOIN alumno a ON a.id = d.alumno_id
                WHERE a.email = 'menor@fic.com'
                """, String.class));
        assertThat(aceptadoPor).containsExactly("Laura Gómez");
    }

    @Test
    void enModoCompletoAlEnviarQuedaEnRevisionYTodaviaNoPuedeEntrar() throws Exception {
        Cookie ficha = sesionDe(abrir(COMPLETO, inscribirConCorreo(COMPLETO, recepcionCompleto, "revision@fic.com", "1990-05-10")));

        enviarFicha(COMPLETO, ficha, DATOS_ADULTO, DOCS_ADULTO).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));

        Map<String, Object> fila = enGimnasio(completo, () -> base.jdbc().queryForMap("""
                SELECT a.estado AS alumno, u.estado AS usuario FROM alumno a JOIN usuario u ON u.id = a.usuario_id
                WHERE a.email = 'revision@fic.com'
                """));
        assertThat(fila).containsEntry("alumno", "EN_REVISION").containsEntry("usuario", "EN_REVISION");
        assertThat(enGimnasio(completo, () -> usuarios.rolesDe("revision@fic.com"))).as("sin rol hasta la aprobación").isEmpty();
    }

    // ------------------------------------------------------------- mostrador

    @Test
    void laRecepcionCompletaLaFichaDeQuienNoTieneCorreo() throws Exception {
        long id = inscribirSinCorreo(SIMPLE, recepcionSimple, "Rosa", "Mostrador");

        mvc.perform(get("http://" + SIMPLE + "/api/inscripciones/" + id + "/ficha").cookie(recepcionSimple))
                .andExpect(status().isOk()).andExpect(jsonPath("$.apellido").value("Mostrador"));
        enviarJson(post("http://" + SIMPLE + "/api/inscripciones/" + id + "/ficha/enviar"), recepcionSimple,
                "{\"datos\":" + DATOS_ADULTO + ",\"documentos\":" + DOCS_ADULTO + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACTIVO"));

        long recepcionId = enGimnasio(simple, () -> usuarios.idDe("recepcion@fic.com").orElseThrow());
        List<Map<String, Object>> aceptaciones = enGimnasio(simple, () -> base.jdbc().queryForList("""
                SELECT d.medio, d.registrado_por FROM aceptacion_documento d JOIN alumno a ON a.id = d.alumno_id
                WHERE a.apellido = 'Mostrador'
                """));
        assertThat(aceptaciones).hasSize(2).allSatisfy(a -> {
            assertThat(a.get("medio")).isEqualTo("MOSTRADOR");
            assertThat(((Number) a.get("registrado_por")).longValue()).isEqualTo(recepcionId);
        });
    }

    @Test
    void unaFichaYaEnviadaNoSePuedeModificar() throws Exception {
        long id = inscribirSinCorreo(SIMPLE, recepcionSimple, "Ya", "Enviada");
        enviarJson(post("http://" + SIMPLE + "/api/inscripciones/" + id + "/ficha/enviar"), recepcionSimple,
                "{\"datos\":" + DATOS_ADULTO + ",\"documentos\":" + DOCS_ADULTO + "}").andExpect(status().isOk());

        enviarJson(put("http://" + SIMPLE + "/api/inscripciones/" + id + "/ficha"), recepcionSimple, DATOS_ADULTO)
                .andExpect(status().isConflict());
    }

    @Test
    void unAlumnoNoPuedeCompletarFichasDeOtros() throws Exception {
        long id = inscribirSinCorreo(SIMPLE, recepcionSimple, "Ajena", "Ficha");
        mvc.perform(get("http://" + SIMPLE + "/api/inscripciones/" + id + "/ficha").cookie(alumnoSimple))
                .andExpect(status().isForbidden());
    }

    @Test
    void elEnvioQuedaEnLaAuditoria() throws Exception {
        long id = inscribirSinCorreo(SIMPLE, recepcionSimple, "Audi", "Ficha");
        enviarJson(post("http://" + SIMPLE + "/api/inscripciones/" + id + "/ficha/enviar"), recepcionSimple,
                "{\"datos\":" + DATOS_ADULTO + ",\"documentos\":" + DOCS_ADULTO + "}").andExpect(status().isOk());

        List<String> acciones = enGimnasio(simple, () -> base.jdbc().queryForList(
                "SELECT accion FROM auditoria WHERE (recurso = 'inscripcion' AND recurso_id = ?) OR "
                        + "(recurso = 'alumno' AND recurso_id = (SELECT alumno_id FROM inscripcion WHERE id = ?)) ORDER BY id",
                String.class, id, id));
        assertThat(acciones).containsExactly("INSCRIPCION_CARGADA", "FICHA_ENVIADA", "ALUMNO_ACTIVADO");
    }

    // ------------------------------------------------------------- utilidades

    /** Inscribe con correo en el mostrador y devuelve el token que llegó en la invitación. */
    private String inscribirConCorreo(String host, Cookie recepcion, String email, String nacimiento) throws Exception {
        enviarJson(post("http://" + host + "/api/inscripciones"), recepcion,
                "{\"nombre\":\"Nombre\",\"apellido\":\"Apellido\",\"email\":\"" + email
                        + "\",\"celular\":\"1155551234\",\"fechaNacimiento\":\"" + nacimiento + "\"}")
                .andExpect(status().isCreated());
        Matcher m = TOKEN.matcher(CorreosEnMemoria.ultimoPara(email).orElseThrow().texto());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private long inscribirSinCorreo(String host, Cookie recepcion, String nombre, String apellido) throws Exception {
        String cuerpo = enviarJson(post("http://" + host + "/api/inscripciones"), recepcion,
                "{\"nombre\":\"" + nombre + "\",\"apellido\":\"" + apellido + "\",\"celular\":\"1155551234\",\"fechaNacimiento\":\"1960-02-02\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private ResultActions abrir(String host, String token) throws Exception {
        return enviarJson(post("http://" + host + "/api/invitacion/abrir"), null, "{\"token\":\"" + token + "\"}");
    }

    private ResultActions enviarFicha(String host, Cookie ficha, String datos, String documentos) throws Exception {
        return enviarJson(post("http://" + host + "/api/ficha/enviar"), ficha, "{\"datos\":" + datos + ",\"documentos\":" + documentos + "}");
    }

    private ResultActions enviarJson(MockHttpServletRequestBuilder pedido, Cookie sesion, String json) throws Exception {
        String host = pedido.buildRequest(new org.springframework.mock.web.MockServletContext()).getServerName();
        Cookie csrf = cookieCsrf(host);
        pedido.header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(sesion == null ? pedido.cookie(csrf) : pedido.cookie(csrf, sesion));
    }

    private static Cookie sesionDe(ResultActions resultado) throws Exception {
        MvcResult r = resultado.andExpect(status().isOk()).andReturn();
        Cookie sesion = r.getResponse().getCookie("__Host-SESION");
        assertThat(sesion).as("abrir la invitación crea la sesión de ficha").isNotNull();
        return sesion;
    }

    private Cookie sesionConClave(GimnasioActual gimnasio, String host, String email, String rol) throws Exception {
        enGimnasio(gimnasio, () -> {
            usuarios.crearActivoSiNoExiste(email, "Personal de prueba", CLAVE);
            usuarios.asignarRol(email, rol);
            return null;
        });
        Cookie csrf = cookieCsrf(host);
        Cookie sesion = mvc.perform(post("http://" + host + "/api/sesion/ingresar").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"clave\":\"" + CLAVE + "\"}"))
                .andExpect(status().isNoContent()).andReturn().getResponse().getCookie("__Host-SESION");
        assertThat(sesion).isNotNull();
        return sesion;
    }

    private Cookie cookieCsrf(String host) throws Exception {
        return mvc.perform(get("http://" + host + "/api/sesion/csrf")).andReturn().getResponse().getCookie("__Host-XSRF-TOKEN");
    }

    private GimnasioActual alta(String slug, String nombre, String host) {
        ResultadoAlta resultado = aprovisionamiento.darDeAlta(new AltaGimnasio(slug, nombre, List.of(host)));
        return new GimnasioActual(resultado.gimnasioId(), slug, nombre, ZoneId.of("UTC"));
    }

    private static <T> T enGimnasio(GimnasioActual gimnasio, Callable<T> operacion) throws Exception {
        return ContextoGimnasio.ejecutarPara(gimnasio, operacion);
    }
}
