package com.gimnasio.plataforma.secretos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de secretos (ADR-0002 §2).
 *
 * @param claveMaestra clave AES de 256 bits en Base64 (variable GYM_CLAVE_MAESTRA). NUNCA en el repo.
 * @param versionClave versión de la clave maestra actual (para rotarla en el futuro).
 */
@ConfigurationProperties("gym.secretos")
public record PropiedadesSecretos(String claveMaestra, @DefaultValue("1") int versionClave) {

    /** Evita que la clave aparezca en logs si alguien imprime este objeto. */
    @Override
    public String toString() {
        return "PropiedadesSecretos[claveMaestra=***, versionClave=" + versionClave + "]";
    }
}
