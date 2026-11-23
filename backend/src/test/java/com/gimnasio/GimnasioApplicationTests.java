package com.gimnasio;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: verifica que el contexto de Spring arranca sin errores de configuración.
 * Es el test más barato que detecta, por ejemplo, una dependencia mal declarada.
 */
@SpringBootTest
class GimnasioApplicationTests {

    @Test
    void elContextoDeSpringArranca() {
        // Si el contexto no puede crearse, el test falla antes de llegar acá.
    }
}
