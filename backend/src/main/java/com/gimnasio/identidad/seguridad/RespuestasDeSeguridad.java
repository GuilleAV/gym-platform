package com.gimnasio.identidad.seguridad;

import java.io.IOException;

import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/** Respuestas de error de los filtros de seguridad: JSON genérico, sin detalles internos. */
final class RespuestasDeSeguridad {

    private RespuestasDeSeguridad() {
    }

    static void noAutorizado(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401}");
    }
}
