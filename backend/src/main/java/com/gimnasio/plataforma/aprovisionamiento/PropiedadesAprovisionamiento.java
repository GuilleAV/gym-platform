package com.gimnasio.plataforma.aprovisionamiento;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del alta de gimnasios.
 *
 * @param usuario    usuario MySQL con permisos de alta (gp_aprovisionador).
 * @param password   su clave (variable GP_APROVISIONADOR_DB_PASSWORD). NUNCA en el repo.
 * @param gimnasios  gimnasios a dar de alta al arrancar (perfil "demo"). Vacío = ninguno.
 */
@ConfigurationProperties("gym.aprovisionamiento")
public record PropiedadesAprovisionamiento(
        @DefaultValue("gp_aprovisionador") String usuario,
        String password,
        @DefaultValue List<AltaGimnasio> gimnasios) {

    @Override
    public String toString() {
        return "PropiedadesAprovisionamiento[usuario=" + usuario + ", password=***, gimnasios=" + gimnasios + "]";
    }
}
