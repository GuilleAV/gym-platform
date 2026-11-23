package com.gimnasio.monitoreo;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Arma las piezas del monitoreo de disco. Solo se activa con {@code gym.monitoreo.disco.habilitado=true}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(MonitoreoDiscoProperties.class)
@ConditionalOnProperty(prefix = "gym.monitoreo.disco", name = "habilitado", havingValue = "true")
class MonitoreoConfig {

    @Bean
    MonitorEspacioDisco monitorEspacioDisco(MonitoreoDiscoProperties propiedades,
            ObjectProvider<JavaMailSender> enviadorCorreo) {
        List<NotificadorAlertas> notificadores = new ArrayList<>();
        JavaMailSender correo = enviadorCorreo.getIfAvailable();
        if (propiedades.tieneEmailDestino() && correo != null) {
            notificadores.add(new CorreoNotificadorAlertas(correo, propiedades.emailDestino()));
        }
        return new MonitorEspacioDisco(
                propiedades.rutas(),
                new FileStoreLectorEspacioDisco(),
                EvaluadorAlertaDisco.desde(propiedades),
                notificadores,
                Clock.systemUTC());
    }
}
