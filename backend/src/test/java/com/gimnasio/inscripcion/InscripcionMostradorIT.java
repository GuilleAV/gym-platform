package com.gimnasio.inscripcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.HexFormat;
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
import org.springframework.test.web.servlet.ResultActions;

import com.gimnasio.auditoria.Auditoria;
import com.gimnasio.identidad.ServicioRoles;
import com.gimnasio.identidad.ServicioUsuarios;
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

/** ADR-0007: inscripción en el mostrador, de punta a punta con MySQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CorreosEnMemoria.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InscripcionMostradorIT extends MysqlPlataformaContenedor {

    private static final String CLAVE = "clave-larga-de-prueba-123";
    private static final String NORTE = "ins-norte.localhost";
    private static final String SUR = "ins-sur.localhost";
    private static final Pattern ENLACE = Pattern.compile("(http://ins-norte\\.localhost/invitacion#t=([A-Za-z0-9_-]{43}))");

    @Autowired MockMvc mvc;
    @Autowired ServicioAprovisionamiento aprovisionamiento;
    @Autowired ServicioUsuarios usuarios;
    @Autowired ServicioRoles roles;
    @Autowired Auditoria auditoria;
    @Autowired BaseDelGimnasio base;

    private GimnasioActual norte;
    private GimnasioActual sur;
    private Cookie recepcionNorte;
    private Cookie adminNorte;
    private Cookie alumnoNorte;
    private Cookie sinRolNorte;
    private Cookie recepcionSur;

    @BeforeAll
    void preparar() throws Exception {
        norte = alta("ins-norte", "Ins Norte", NORTE);
        sur = alta("ins-sur", "Ins Sur", SUR);
        for (GimnasioActual g : List.of(norte, sur)) {
            // En estos gimnasios de prueba, el personal entra sin MFA (el MFA ya tiene sus propios tests).
            enGimnasio(g, () -> roles.cambiarMfa("ADMINISTRADOR", false) && roles.cambiarMfa("RECEPCION", false));
        }
        recepcionNorte = sesion(norte, NORTE, "recepcion@ins.com", "RECEPCION");
        adminNorte = sesion(norte, NORTE, "admin@ins.com", "ADMINISTRADOR");
        alumnoNorte = sesion(norte, NORTE, "alumno-viejo@ins.com", "ALUMNO");
        sinRolNorte = sesion(norte, NORTE, "sin-rol@ins.com", null);
        recepcionSur = sesion(sur, SUR, "recepcion@ins.com", "RECEPCION");
    }

    // ------------------------------------------------------------ carga feliz

    @Test
    void laRecepcionInscribeConCorreoYLeLlegaLaInvitacion() throws Exception {
        inscribir(NORTE, recepcionNorte, pedido("Ana", "Pérez", "ana@alumnos.com", "1990-05-10"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("FICHA_PENDIENTE"))
                .andExpect(jsonPath("$.invitacionEnviada").value(true))
                .andExpect(jsonPath("$.esMenor").value(false));

        Correo correo = CorreosEnMemoria.ultimoPara("ana@alumnos.com").orElseThrow();
        assertThat(correo.asunto()).isEqualTo("Completá tu inscripción en Ins Norte");
        Matcher enlace = ENLACE.matcher(correo.texto());
        assertThat(enlace.find()).as("el correo trae el enlace con el token en el fragmento (#t=)").isTrue();

        Map<String, Object> fila = enGimnasio(norte, () -> base.jdbc().queryForMap("""
                SELECT u.estado AS estado_usuario, u.hash_clave, a.estado AS estado_alumno, i.cargada_por, inv.hash_token
                FROM alumno a JOIN usuario u ON u.id = a.usuario_id JOIN inscripcion i ON i.alumno_id = a.id
                JOIN invitacion inv ON inv.inscripcion_id = i.id WHERE a.email = 'ana@alumnos.com'
                """));
        assertThat(fila.get("estado_usuario")).as("cuenta creada, pero solo invitada").isEqualTo("INVITADO");
        assertThat(fila.get("hash_clave")).as("sin contraseña (ADR-0006)").isNull();
        assertThat(fila.get("estado_alumno")).isEqualTo("FICHA_INCOMPLETA");
        assertThat(((Number) fila.get("cargada_por")).longValue())
                .isEqualTo(enGimnasio(norte, () -> usuarios.idDe("recepcion@ins.com").orElseThrow()));
        assertThat(fila.get("hash_token")).as("se guarda el SHA-256, no el token").isEqualTo(sha256(enlace.group(2)));
    }

    @Test
    void sinCorreoQuedaInscriptoPeroSinCuentaNiInvitacion() throws Exception {
        inscribir(NORTE, recepcionNorte, pedido("Rosa", "Sin Correo", null, "1950-01-20"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitacionEnviada").value(false));

        Map<String, Object> alumno = enGimnasio(norte, () -> base.jdbc().queryForMap(
                "SELECT usuario_id, email FROM alumno WHERE apellido = 'Sin Correo'"));
        assertThat(alumno.get("usuario_id")).isNull();
        assertThat(alumno.get("email")).isNull();
    }

    @Test
    void elAdministradorTambienPuedeInscribir() throws Exception {
        inscribir(NORTE, adminNorte, pedido("Beto", "Admin", "beto@alumnos.com", "1985-03-03")).andExpect(status().isCreated());
    }

    @Test
    void unMenorQuedaMarcadoParaPedirLosDatosDelResponsable() throws Exception {
        String hace15 = java.time.LocalDate.now(ZoneId.of("UTC")).minusYears(15).toString();
        inscribir(NORTE, recepcionNorte, pedido("Leo", "Menor", "leo@alumnos.com", hace15))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.esMenor").value(true));
    }

    // ---------------------------------------------------------------- permisos

    @Test
    void yoInformaLosRolesParaQueAngularMuestreElMenuCorrecto() throws Exception {
        mvc.perform(get("http://" + NORTE + "/api/sesion/yo").cookie(recepcionNorte))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("RECEPCION"));
    }

    @Test
    void sinSesionDa401() throws Exception {
        inscribir(NORTE, null, pedido("X", "Y", null, "1990-01-01")).andExpect(status().isUnauthorized());
    }

    @Test
    void unAlumnoOUnUsuarioSinRolNoPuedenInscribir() throws Exception {
        inscribir(NORTE, alumnoNorte, pedido("X", "Y", null, "1990-01-01")).andExpect(status().isForbidden());
        inscribir(NORTE, sinRolNorte, pedido("X", "Y", null, "1990-01-01")).andExpect(status().isForbidden());
        mvc.perform(get("http://" + NORTE + "/api/inscripciones").cookie(alumnoNorte)).andExpect(status().isForbidden());
    }

    @Test
    void laSesionDeUnGimnasioNoSirveParaInscribirEnOtro() throws Exception {
        inscribir(SUR, recepcionNorte, pedido("Cruz", "Ado", null, "1990-01-01")).andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------- errores de datos

    @Test
    void conDatosInvalidosDa400ConLaListaDeErrores() throws Exception {
        inscribir(NORTE, recepcionNorte, "{\"nombre\":\"\",\"apellido\":\"P\",\"email\":\"mal\",\"celular\":\"x\",\"fechaNacimiento\":\"2999-01-01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(4));
    }

    @Test
    void siElCorreoYaExisteEnElGimnasioAvisaYNoCreaNada() throws Exception {
        int antes = cantidadDeAlumnos(norte);

        inscribir(NORTE, recepcionNorte, pedido("Otra", "Recepcion", "recepcion@ins.com", "1990-01-01"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Ese correo ya está registrado en este gimnasio"));

        assertThat(cantidadDeAlumnos(norte)).isEqualTo(antes);
    }

    @Test
    void elMismoCorreoPuedeInscribirseEnOtroGimnasio() throws Exception {
        inscribir(NORTE, recepcionNorte, pedido("Dos", "Gyms", "dos-gyms@alumnos.com", "1990-01-01")).andExpect(status().isCreated());
        inscribir(SUR, recepcionSur, pedido("Dos", "Gyms", "dos-gyms@alumnos.com", "1990-01-01")).andExpect(status().isCreated());
    }

    // --------------------------------------------------------- lista y reenvío

    @Test
    void laListaEsDeCadaGimnasioYSeFiltraPorEstado() throws Exception {
        inscribir(NORTE, recepcionNorte, pedido("Solo", "Norte", null, "1990-01-01")).andExpect(status().isCreated());

        String listaNorte = mvc.perform(get("http://" + NORTE + "/api/inscripciones?estado=FICHA_PENDIENTE").cookie(recepcionNorte))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String listaSur = mvc.perform(get("http://" + SUR + "/api/inscripciones").cookie(recepcionSur))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(listaNorte).contains("\"apellido\":\"Norte\"");
        assertThat(listaSur).doesNotContain("\"apellido\":\"Norte\"");
        mvc.perform(get("http://" + NORTE + "/api/inscripciones?estado=CUALQUIERA").cookie(recepcionNorte))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reenviarLaInvitacionAnulaLaAnteriorYRespetaLaEsperaDeUnMinuto() throws Exception {
        long id = idDe(inscribir(NORTE, recepcionNorte, pedido("Re", "Envio", "reenvio@alumnos.com", "1990-01-01")));

        reenviar(NORTE, recepcionNorte, id).andExpect(status().isConflict()); // recién se mandó
        enGimnasio(norte, () -> base.jdbc().update("UPDATE invitacion SET creada_ms = creada_ms - 120000 WHERE inscripcion_id = ?", id));
        reenviar(NORTE, recepcionNorte, id).andExpect(status().isAccepted());

        assertThat(CorreosEnMemoria.para("reenvio@alumnos.com")).hasSize(2);
        List<Long> vigentes = enGimnasio(norte, () -> base.jdbc().queryForList(
                "SELECT id FROM invitacion WHERE inscripcion_id = ? AND usada_ms IS NULL", Long.class, id));
        assertThat(vigentes).as("solo la última sirve").hasSize(1);
    }

    @Test
    void reenviarSinCorreoOInexistenteNoCorresponde() throws Exception {
        long sinCorreo = idDe(inscribir(NORTE, recepcionNorte, pedido("Sin", "Mail", null, "1990-01-01")));
        reenviar(NORTE, recepcionNorte, sinCorreo).andExpect(status().isConflict());
        reenviar(NORTE, recepcionNorte, 999_999).andExpect(status().isNotFound());
    }

    @Test
    void unaInscripcionDeOtroGimnasioNoSePuedeTocar() throws Exception {
        long deSur = idDe(inscribir(SUR, recepcionSur, pedido("De", "Sur", "de-sur@alumnos.com", "1990-01-01")));
        // El mismo id en la base de Norte no existe (o es otra inscripción): nunca toca la de Sur.
        enGimnasio(sur, () -> base.jdbc().update("UPDATE invitacion SET creada_ms = creada_ms - 120000 WHERE inscripcion_id = ?", deSur));

        reenviar(NORTE, recepcionNorte, deSur);

        assertThat(CorreosEnMemoria.para("de-sur@alumnos.com")).hasSize(1);
    }

    // ------------------------------------------------- auditoría y transacción

    @Test
    void laCargaYLaInvitacionQuedanEnLaAuditoriaConQuienLasHizo() throws Exception {
        long id = idDe(inscribir(NORTE, recepcionNorte, pedido("Audi", "Toria", "audi@alumnos.com", "1990-01-01")));
        long recepcionId = enGimnasio(norte, () -> usuarios.idDe("recepcion@ins.com").orElseThrow());

        List<String> acciones = enGimnasio(norte, () -> base.jdbc().queryForList(
                "SELECT CONCAT(actor, ' ', accion) FROM auditoria WHERE recurso = 'inscripcion' AND recurso_id = ? ORDER BY id",
                String.class, id));
        assertThat(acciones).containsExactly("usuario:" + recepcionId + " INSCRIPCION_CARGADA",
                "usuario:" + recepcionId + " INVITACION_ENVIADA");
    }

    @Test
    void siAlgoFallaAdentroDeLaTransaccionNoQuedaNadaGuardado() throws Exception {
        assertThatThrownBy(() -> enGimnasio(norte, () -> base.enTransaccion(() -> {
            auditoria.registrar("sistema", "PRUEBA_ROLLBACK", "prueba", null, null);
            throw new IllegalStateException("falla a propósito");
        }))).isInstanceOf(IllegalStateException.class);

        Integer filas = enGimnasio(norte, () -> base.jdbc().queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE accion = 'PRUEBA_ROLLBACK'", Integer.class));
        assertThat(filas).isZero();
    }

    @Test
    void unInvitadoTodaviaNoPuedeEntrarConCodigo() throws Exception {
        inscribir(NORTE, recepcionNorte, pedido("In", "Vitado", "invitado@alumnos.com", "1990-01-01")).andExpect(status().isCreated());
        int correosAntes = CorreosEnMemoria.para("invitado@alumnos.com").size();
        Cookie csrf = cookieCsrf(NORTE);

        mvc.perform(post("http://" + NORTE + "/api/sesion/codigo/solicitar").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"invitado@alumnos.com\"}")).andExpect(status().isAccepted());

        assertThat(CorreosEnMemoria.para("invitado@alumnos.com")).as("hasta terminar la ficha, no recibe códigos").hasSize(correosAntes);
    }

    // ------------------------------------------------------------- utilidades

    private static String pedido(String nombre, String apellido, String email, String nacimiento) {
        return "{\"nombre\":\"%s\",\"apellido\":\"%s\",%s\"celular\":\"1155551234\",\"fechaNacimiento\":\"%s\"}"
                .formatted(nombre, apellido, email == null ? "" : "\"email\":\"" + email + "\",", nacimiento);
    }

    private ResultActions inscribir(String host, Cookie sesion, String json) throws Exception {
        Cookie csrf = cookieCsrf(host);
        var pedido = post("http://" + host + "/api/inscripciones").header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(sesion == null ? pedido.cookie(csrf) : pedido.cookie(csrf, sesion));
    }

    private ResultActions reenviar(String host, Cookie sesion, long id) throws Exception {
        Cookie csrf = cookieCsrf(host);
        return mvc.perform(post("http://" + host + "/api/inscripciones/" + id + "/reenviar-invitacion")
                .header("X-XSRF-TOKEN", csrf.getValue()).cookie(csrf, sesion));
    }

    private static long idDe(ResultActions resultado) throws Exception {
        String cuerpo = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id")).longValue();
    }

    /** Crea el usuario con contraseña y rol, entra con la contraseña y devuelve la cookie de sesión. */
    private Cookie sesion(GimnasioActual gimnasio, String host, String email, String rol) throws Exception {
        enGimnasio(gimnasio, () -> {
            usuarios.crearActivoSiNoExiste(email, "Personal de prueba", CLAVE);
            if (rol != null) {
                usuarios.asignarRol(email, rol);
            }
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

    private int cantidadDeAlumnos(GimnasioActual gimnasio) throws Exception {
        return enGimnasio(gimnasio, () -> base.jdbc().queryForObject("SELECT COUNT(*) FROM alumno", Integer.class));
    }

    private static String sha256(String texto) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.US_ASCII)));
    }

    private GimnasioActual alta(String slug, String nombre, String host) {
        ResultadoAlta resultado = aprovisionamiento.darDeAlta(new AltaGimnasio(slug, nombre, List.of(host)));
        return new GimnasioActual(resultado.gimnasioId(), slug, nombre, ZoneId.of("UTC"));
    }

    private static <T> T enGimnasio(GimnasioActual gimnasio, Callable<T> operacion) throws Exception {
        return ContextoGimnasio.ejecutarPara(gimnasio, operacion);
    }
}
