package com.gimnasio.plataforma.resolucion;

import java.io.IOException;
import java.util.Optional;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gimnasio.plataforma.ContextoGimnasio;
import com.gimnasio.plataforma.GimnasioActual;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Primer filtro de TODOS los requests: decide a qué gimnasio pertenece, o lo rechaza.
 *
 * <p>Secuencia segura (Arquitectura §3):</p>
 * <ol>
 *   <li>Toma el host del request ({@code getServerName()}, que sale del header Host).</li>
 *   <li>Lo normaliza y lo busca en el catálogo de dominios registrados.</li>
 *   <li>Si no está, o el gimnasio no está activo: 404 genérico, sin detalles (no le contamos a un
 *       curioso qué dominios existen ni por qué falló).</li>
 *   <li>Si está: ejecuta el resto del request DENTRO del contexto de ese gimnasio.</li>
 * </ol>
 *
 * <p><b>Lo que NO hace, a propósito:</b> no lee ningún {@code gimnasioId} de parámetros, headers ni
 * cuerpo, y no confía en {@code X-Forwarded-Host} (Spring Boot no los usa por defecto). El día que
 * haya un proxy delante, se configura explícitamente qué proxy es confiable.</p>
 *
 * <p>Orden: corre antes que cualquier otro filtro (sesión, seguridad), porque esos necesitan saber
 * en qué base buscar (ADR-0002).</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class FiltroResolucionGimnasio extends OncePerRequestFilter {

    private static final String RESPUESTA_NO_ENCONTRADO =
            "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404}";

    private final CatalogoDominios catalogo;

    FiltroResolucionGimnasio(CatalogoDominios catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {

        Optional<GimnasioActual> gimnasio = NormalizadorHost.normalizar(request.getServerName())
                .flatMap(catalogo::buscarPorHost);

        if (gimnasio.isEmpty()) {
            responderNoEncontrado(response);
            return;
        }

        try {
            ContextoGimnasio.ejecutarPara(gimnasio.get(), () -> {
                cadena.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    /**
     * Si un request falla, Tomcat hace un segundo pasaje interno hacia /error. Ese pasaje también
     * necesita el gimnasio (la sesión vive en su base), así que el filtro corre ahí también.
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private static void responderNoEncontrado(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(RESPUESTA_NO_ENCONTRADO);
    }
}
