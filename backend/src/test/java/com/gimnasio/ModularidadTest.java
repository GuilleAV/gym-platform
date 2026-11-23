package com.gimnasio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Guardián de la arquitectura del monolito modular (Spring Modulith). Es unitario: no arranca
 * Spring ni necesita Docker; analiza el bytecode con ArchUnit.
 *
 * <p><b>Qué es un módulo:</b> cada paquete DIRECTO bajo {@code com.gimnasio} (hoy
 * {@code monitoreo}; después {@code socios}, {@code caja}, {@code rutinas}...). Lo que está en el
 * paquete raíz del módulo es su API pública; los subpaquetes (ej. {@code socios.interno}) son
 * privados del módulo.</p>
 *
 * <p><b>Qué verifica {@code verify()}:</b></p>
 * <ul>
 *   <li>Que ningún módulo use clases de los subpaquetes internos de otro.</li>
 *   <li>Que no haya ciclos entre módulos (A usa B y B usa A).</li>
 * </ul>
 *
 * <p><b>Por qué importa:</b> en un monolito "todo es público" y con el tiempo cualquier clase usa
 * cualquier otra ("código spaghetti"). Con este test, romper un límite rompe el build en el PR,
 * no meses después. Y si algún día un módulo tiene que salir como servicio aparte, sus
 * dependencias ya están acotadas.</p>
 */
class ModularidadTest {

    private static final ApplicationModules MODULOS = ApplicationModules.of(GimnasioApplication.class);

    @Test
    void losModulosRespetanSusLimitesYNoTienenCiclos() {
        MODULOS.verify();
    }

    /**
     * Protege al test anterior de pasar "en vacío": si alguien moviera GimnasioApplication a otro
     * paquete, Modulith no encontraría módulos y verify() pasaría sin verificar nada.
     */
    @Test
    void detectaLosModulosEsperados() {
        assertThat(MODULOS.getModuleByName("monitoreo"))
                .as("monitoreo debe detectarse como módulo de aplicación")
                .isPresent();
    }

    /**
     * Genera diagramas PlantUML de los módulos y sus dependencias en
     * {@code target/spring-modulith-docs}. Documentación que nunca queda desactualizada,
     * porque sale del código en cada build.
     */
    @Test
    void generaLaDocumentacionDeModulos() {
        new Documenter(MODULOS)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
