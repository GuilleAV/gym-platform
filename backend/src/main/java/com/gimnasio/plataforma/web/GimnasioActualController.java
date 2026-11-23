package com.gimnasio.plataforma.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gimnasio.plataforma.ContextoGimnasio;
import com.gimnasio.plataforma.GimnasioActual;

/**
 * {@code GET /api/gimnasio}: datos públicos del gimnasio del dominio (los usará la landing).
 *
 * <p>Fijate que el método no recibe ningún parámetro: el gimnasio sale del contexto que armó el
 * filtro a partir del dominio. No hay forma de pedir "los datos del gimnasio 2" desde el cliente.</p>
 */
@RestController
class GimnasioActualController {

    @GetMapping("/api/gimnasio")
    Map<String, String> gimnasioActual() {
        GimnasioActual gimnasio = ContextoGimnasio.actual();
        // No se expone el ID interno: el cliente no lo necesita.
        return Map.of(
                "slug", gimnasio.slug(),
                "nombre", gimnasio.nombre(),
                "zonaHoraria", gimnasio.zonaHoraria().getId());
    }
}
