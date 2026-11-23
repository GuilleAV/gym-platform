package com.gimnasio.monitoreo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Con la configuración por defecto de application.yml, el monitor de disco queda activo. */
@SpringBootTest(properties = "gym.monitoreo.disco.demora-inicial=PT1H")
class MonitoreoContextoTest {

    @Autowired
    ApplicationContext contexto;

    @Test
    void elMonitorEstaRegistrado() {
        assertThat(contexto.getBeansOfType(MonitorEspacioDisco.class)).hasSize(1);
    }
}
