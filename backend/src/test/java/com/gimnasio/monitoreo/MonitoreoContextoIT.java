package com.gimnasio.monitoreo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.gimnasio.soporte.MysqlPlataformaContenedor;

/**
 * Con la configuración por defecto de application.yml, el monitor de disco queda activo.
 * Es de integración (IT) porque arrancar Spring ahora necesita la base del catálogo.
 */
@SpringBootTest
class MonitoreoContextoIT extends MysqlPlataformaContenedor {

    @Autowired
    ApplicationContext contexto;

    @Test
    void elMonitorEstaRegistrado() {
        assertThat(contexto.getBeansOfType(MonitorEspacioDisco.class)).hasSize(1);
    }
}
