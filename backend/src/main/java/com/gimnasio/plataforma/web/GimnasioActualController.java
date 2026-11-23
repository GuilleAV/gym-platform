package com.gimnasio.plataforma.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gimnasio.plataforma.BaseDelGimnasio;
import com.gimnasio.plataforma.ContextoGimnasio;
import com.gimnasio.plataforma.GimnasioActual;

/**
 * Datos públicos del gimnasio del dominio (los usará la landing).
 *
 * <p>Fijate que ningún método recibe parámetros: el gimnasio sale del contexto que armó el filtro a
 * partir del dominio. No hay forma de pedir "los datos del gimnasio 2" desde el cliente.</p>
 */
@RestController
class GimnasioActualController {

    private final BaseDelGimnasio base;

    GimnasioActualController(BaseDelGimnasio base) {
        this.base = base;
    }

    /** {@code GET /api/gimnasio}: sale del CATÁLOGO (gym_platform). */
    @GetMapping("/api/gimnasio")
    Map<String, String> gimnasioActual() {
        GimnasioActual gimnasio = ContextoGimnasio.actual();
        // No se expone el ID interno: el cliente no lo necesita.
        return Map.of(
                "slug", gimnasio.slug(),
                "nombre", gimnasio.nombre(),
                "zonaHoraria", gimnasio.zonaHoraria().getId());
    }

    /** {@code GET /api/gimnasio/bienvenida}: sale de la BASE PROPIA del gimnasio (gym_NNN). */
    @GetMapping("/api/gimnasio/bienvenida")
    Map<String, String> bienvenida() {
        String mensaje = base.jdbc()
                .queryForList("SELECT valor FROM parametro_gimnasio WHERE clave = 'bienvenida'", String.class)
                .stream().findFirst().orElse("");
        return Map.of("mensaje", mensaje);
    }
}
