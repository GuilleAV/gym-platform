package com.gimnasio.plataforma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.gimnasio.soporte.MysqlPlataformaContenedor;

/**
 * Paso 0.4: el gimnasio se resuelve por el DOMINIO y nada más.
 *
 * <p>MockMvc simula requests HTTP completos (con los filtros) sin levantar un servidor real.
 * Cada test crea sus gimnasios y, por {@code @Transactional}, todo se deshace al terminar.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResolucionGimnasioIT extends MysqlPlataformaContenedor {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void gimnasios() {
        long norte = gimnasio("gym-norte", "Gym Norte", "ACTIVO");
        long powerFit = gimnasio("power-fit", "Power Fit", "ACTIVO");
        long suspendido = gimnasio("moroso", "Gimnasio Moroso", "SUSPENDIDO");

        dominio(norte, "gym-norte.localhost", true);
        dominio(powerFit, "power-fit.localhost", true);
        dominio(norte, "viejo-norte.localhost", false); // dominio dado de baja
        dominio(suspendido, "moroso.localhost", true);
    }

    // ------------------------------------------------------------ camino feliz

    @Test
    void cadaDominioRespondeConSuPropioGimnasio() throws Exception {
        mvc.perform(get("http://gym-norte.localhost/api/gimnasio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("gym-norte"))
                .andExpect(jsonPath("$.nombre").value("Gym Norte"));

        mvc.perform(get("http://power-fit.localhost/api/gimnasio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("power-fit"));
    }

    @Test
    void elDominioSeReconoceSinImportarMayusculas() throws Exception {
        mvc.perform(get("http://GYM-NORTE.Localhost/api/gimnasio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("gym-norte"));
    }

    @Test
    void noSeExponeElIdInterno() throws Exception {
        mvc.perform(get("http://gym-norte.localhost/api/gimnasio"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    // ------------------------------------------------- denegar por defecto

    @Test
    void unDominioDesconocidoRecibe404SinDetalles() throws Exception {
        mvc.perform(get("http://otro-gimnasio.localhost/api/gimnasio"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("otro-gimnasio"))));
    }

    @Test
    void unGimnasioSuspendidoSeTrataComoDesconocido() throws Exception {
        mvc.perform(get("http://moroso.localhost/api/gimnasio")).andExpect(status().isNotFound());
    }

    @Test
    void unDominioDadoDeBajaSeTrataComoDesconocido() throws Exception {
        mvc.perform(get("http://viejo-norte.localhost/api/gimnasio")).andExpect(status().isNotFound());
    }

    // ------------------------------- el cliente NO elige el gimnasio

    @Test
    void ignoraCualquierIdDeGimnasioQueMandeElCliente() throws Exception {
        mvc.perform(get("http://gym-norte.localhost/api/gimnasio")
                        .header("X-Gimnasio-Id", "2")
                        .param("gimnasioId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("gym-norte"));
    }

    @Test
    void noConfiaEnXForwardedHost() throws Exception {
        mvc.perform(get("http://gym-norte.localhost/api/gimnasio")
                        .header("X-Forwarded-Host", "power-fit.localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("gym-norte"));
    }

    @Test
    void despuesDelRequestNoQuedaGimnasioEnElContexto() throws Exception {
        mvc.perform(get("http://gym-norte.localhost/api/gimnasio")).andExpect(status().isOk());

        assertThat(ContextoGimnasio.siHay()).as("el contexto no se filtra fuera del request").isEmpty();
    }

    // ------------------------------------------------------------ datos de prueba

    private long gimnasio(String slug, String nombre, String estado) {
        jdbc.update("INSERT INTO gimnasio (slug, nombre, estado) VALUES (?, ?, ?)", slug, nombre, estado);
        return jdbc.queryForObject("SELECT id FROM gimnasio WHERE slug = ?", Long.class, slug);
    }

    private void dominio(long gimnasioId, String host, boolean activo) {
        jdbc.update("INSERT INTO gimnasio_dominio (gimnasio_id, host, activo) VALUES (?, ?, ?)", gimnasioId, host, activo);
    }
}
