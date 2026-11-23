package com.gimnasio.plataforma.aprovisionamiento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, da de alta los gimnasios listados en {@code gym.aprovisionamiento.gimnasios}.
 *
 * <p>En desarrollo se usa con el perfil {@code demo} (application-demo.yml), que lista dos gimnasios
 * de ejemplo. Como el alta es idempotente, reiniciar la app no duplica nada. Los gimnasios de ejemplo
 * están en configuración, no en el código: el código no sabe nada de "gym_001" ni de "gym_002".</p>
 */
@Component
class AltaDesdeConfiguracion implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AltaDesdeConfiguracion.class);

    private final ServicioAprovisionamiento servicio;
    private final PropiedadesAprovisionamiento propiedades;

    AltaDesdeConfiguracion(ServicioAprovisionamiento servicio, PropiedadesAprovisionamiento propiedades) {
        this.servicio = servicio;
        this.propiedades = propiedades;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        for (AltaGimnasio alta : propiedades.gimnasios()) {
            ResultadoAlta resultado = servicio.darDeAlta(alta);
            LOG.info("Alta desde configuración: '{}' -> {} (esquema v{})",
                    alta.slug(), resultado.nombreBd(), resultado.versionEsquema());
        }
    }
}
