package com.gimnasio.plataforma.sesiones;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import com.gimnasio.plataforma.conexiones.PoolsPorGimnasio;

/**
 * Spring Session JDBC guarda las sesiones en la base del gimnasio del request (ADR-0002 §1).
 */
@Configuration(proxyBeanMethods = false)
class SesionesConfig {

    /** Nombre de la cookie de sesión. */
    static final String COOKIE_SESION = "__Host-SESION";

    /**
     * {@code defaultCandidate = false}: este DataSource NO es "el" DataSource de la app. Sin eso,
     * Spring Boot vería dos DataSource y dejaría de configurar el del catálogo (y Flyway, JdbcTemplate...).
     * Solo lo recibe quien lo pide con el calificador {@link SpringSessionDataSource}: Spring Session.
     */
    @Bean(defaultCandidate = false)
    @SpringSessionDataSource
    DataSource fuenteDeSesiones(PoolsPorGimnasio pools) {
        return new FuenteDeDatosDelGimnasioActual(pools);
    }

    /**
     * La cookie de sesión, configurada EN CÓDIGO y no por propiedades: en Spring Boot 4 las propiedades
     * {@code server.servlet.session.cookie.*} no llegaban a Spring Session y la cookie salía como
     * "SESSION" sin Secure ni SameSite (lo detectó SesionIT). Así no depende de esa conexión.
     *
     * <ul>
     *   <li>{@code __Host-}: el navegador exige Secure, Path=/ y SIN Domain. Sin Domain, la cookie de
     *       gym-norte.ejemplo.com nunca se manda a power-fit.ejemplo.com.</li>
     *   <li>Secure: solo viaja por HTTPS (los navegadores tratan *.localhost como seguro).</li>
     *   <li>HttpOnly: el JavaScript de la página no puede leerla (un XSS no la roba).</li>
     *   <li>SameSite=Lax: no viaja en POSTs que vienen de otros sitios.</li>
     * </ul>
     */
    @Bean
    CookieSerializer cookieDeSesion() {
        DefaultCookieSerializer cookie = new DefaultCookieSerializer();
        cookie.setCookieName(COOKIE_SESION);
        cookie.setCookiePath("/");
        cookie.setUseSecureCookie(true);
        cookie.setUseHttpOnlyCookie(true);
        cookie.setSameSite("Lax");
        return cookie;
    }
}
