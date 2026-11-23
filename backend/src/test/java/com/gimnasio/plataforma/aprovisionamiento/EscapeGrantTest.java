package com.gimnasio.plataforma.aprovisionamiento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Regresión del bug encontrado por la CI en el paso 0.5: en GRANT, "_" es un comodín.
 * Sin escapar, "GRANT ... ON gym_033.*" pide permisos sobre el patrón "gym?033" y MySQL lo niega
 * a un usuario que solo tiene permisos sobre gym\_%.
 */
class EscapeGrantTest {

    @Test
    void escapaLosComodinesDelNombreDeLaBase() {
        assertThat(ServicioAprovisionamiento.escaparParaGrant("gym_033")).isEqualTo("gym\\_033");
        assertThat(ServicioAprovisionamiento.escaparParaGrant("gym_1500")).isEqualTo("gym\\_1500");
    }
}
