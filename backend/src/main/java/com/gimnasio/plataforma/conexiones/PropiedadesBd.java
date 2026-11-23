package com.gimnasio.plataforma.conexiones;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dónde está el servidor MySQL, visto desde la aplicación.
 *
 * @param host       ej. 127.0.0.1 en desarrollo.
 * @param puerto     ej. 3307 en desarrollo (docker-compose).
 * @param parametros parámetros del driver (TLS, etc.).
 */
@ConfigurationProperties("gym.bd")
public record PropiedadesBd(String host, int puerto, String parametros) {

    /** URL JDBC hacia una base (o hacia el servidor, si {@code base} es vacío). */
    public String urlPara(String host, int puerto, String base) {
        return "jdbc:mysql://" + host + ":" + puerto + "/" + base + (parametros == null || parametros.isBlank() ? "" : "?" + parametros);
    }

    public String urlPara(String base) {
        return urlPara(host, puerto, base);
    }
}
