package com.gimnasio.plataforma.sesiones;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource;

import com.gimnasio.plataforma.conexiones.PoolsPorGimnasio;

/**
 * Spring Session JDBC guarda las sesiones en la base del gimnasio del request (ADR-0002 §1).
 *
 * <p>{@code defaultCandidate = false}: este DataSource NO es "el" DataSource de la app. Sin eso,
 * Spring Boot vería dos DataSource y dejaría de configurar el del catálogo (y Flyway, JdbcTemplate...).
 * Solo lo recibe quien lo pide con el calificador {@link SpringSessionDataSource}: Spring Session.</p>
 */
@Configuration(proxyBeanMethods = false)
class SesionesConfig {

    @Bean(defaultCandidate = false)
    @SpringSessionDataSource
    DataSource fuenteDeSesiones(PoolsPorGimnasio pools) {
        return new FuenteDeDatosDelGimnasioActual(pools);
    }
}
