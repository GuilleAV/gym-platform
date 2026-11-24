package com.gimnasio.identidad.seguridad;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.savedrequest.NullRequestCache;

import java.time.Clock;
import java.util.List;

import com.gimnasio.identidad.Contrasenias;

/**
 * Configuración de Spring Security (paso 0.6).
 *
 * <ul>
 *   <li><b>Denegar por defecto:</b> todo requiere sesión salvo una lista corta de endpoints públicos.</li>
 *   <li><b>Sesión en cookie HttpOnly</b> (Spring Session, en la base del gimnasio): el JavaScript de la
 *       página no puede leerla, así que un XSS no puede robarla.</li>
 *   <li><b>CSRF:</b> como el navegador manda la cookie solo, otra página podría disparar un POST "en
 *       nombre" del usuario. El token CSRF (cookie legible + header X-XSRF-TOKEN) lo impide: una página
 *       ajena no puede leer la cookie para copiar el valor en el header.</li>
 *   <li><b>Sin formulario de login ni Basic:</b> la API es JSON y responde 401, nunca una redirección.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(PropiedadesSeguridad.class)
class SeguridadConfig {

    /** Cookie del token CSRF con prefijo __Host-: no puede ser pisada desde otro subdominio (otro gimnasio). */
    static final String COOKIE_CSRF = "__Host-XSRF-TOKEN";

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, CookieCsrfTokenRepository tokensCsrf,
            SecurityContextRepository contextos, Clock reloj, PropiedadesSeguridad propiedades) throws Exception {
        http
                .authorizeHttpRequests(permisos -> permisos
                        .requestMatchers(HttpMethod.GET, "/api/gimnasio", "/api/gimnasio/bienvenida", "/api/sesion/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sesion/ingresar").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> {
                    csrf.spa();
                    csrf.csrfTokenRepository(tokensCsrf);
                })
                .securityContext(contexto -> contexto.securityContextRepository(contextos))
                // Sin sesión → 401 (no una redirección a una página de login que no existe).
                .exceptionHandling(errores -> errores.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // No guardar "la página que quería ver" en la sesión: crearía sesiones para anónimos.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(formulario -> formulario.disable())
                .httpBasic(basic -> basic.disable())
                .logout(salir -> salir
                        .logoutUrl("/api/sesion/salir")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .addFilterAfter(new FiltroGimnasioDeLaSesion(), SecurityContextHolderFilter.class)
                // Después de verificar el gimnasio: vida máxima de la sesión desde el ingreso.
                .addFilterAfter(new FiltroVencimientoAbsoluto(reloj, propiedades.sesion().vencimientoAbsoluto()),
                        FiltroGimnasioDeLaSesion.class);
        return http.build();
    }

    /**
     * El reloj de la seguridad (bloqueos, vencimientos). Inyectarlo en vez de llamar a
     * {@code System.currentTimeMillis()} permite que los tests usen un reloj fijo o adelantado.
     */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder codificadorDeContrasenias() {
        return Contrasenias.codificador();
    }

    @Bean
    AuthenticationManager gestorDeAutenticacion(UserDetailsService usuarios, PasswordEncoder codificador) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(usuarios);
        // DaoAuthenticationProvider también calcula un hash "de mentira" cuando el usuario no existe,
        // para que el tiempo de respuesta no delate qué correos están registrados.
        proveedor.setPasswordEncoder(codificador);
        return new ProviderManager(proveedor);
    }

    @Bean
    CookieCsrfTokenRepository tokensCsrf() {
        CookieCsrfTokenRepository repositorio = CookieCsrfTokenRepository.withHttpOnlyFalse(); // Angular necesita leerla
        repositorio.setCookieName(COOKIE_CSRF);
        repositorio.setCookiePath("/");
        repositorio.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Strict"));
        return repositorio;
    }

    @Bean
    SecurityContextRepository repositorioDeContextos() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Al iniciar sesión: nuevo ID de sesión (contra session fixation) y nuevo token CSRF. */
    @Bean
    SessionAuthenticationStrategy estrategiaAlIngresar(CookieCsrfTokenRepository tokensCsrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(tokensCsrf)));
    }
}
