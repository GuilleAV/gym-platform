package com.gimnasio.plataforma;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.gimnasio.plataforma.aprovisionamiento.PropiedadesAprovisionamiento;
import com.gimnasio.plataforma.conexiones.PropiedadesBd;
import com.gimnasio.plataforma.secretos.PropiedadesSecretos;

/** Registra las propiedades del módulo plataforma (gym.bd, gym.secretos, gym.aprovisionamiento). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PropiedadesBd.class, PropiedadesSecretos.class, PropiedadesAprovisionamiento.class})
class PlataformaConfig {
}
