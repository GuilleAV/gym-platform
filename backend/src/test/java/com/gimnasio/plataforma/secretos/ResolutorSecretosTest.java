package com.gimnasio.plataforma.secretos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

/** Unitario: cada tipo de referencia, y que los errores no muestren el valor del secreto. */
class ResolutorSecretosTest {

    private final MockEnvironment entorno = new MockEnvironment().withProperty("CLAVE_DEMO", "valor-demo");
    private final ResolutorSecretos resolutor =
            new ResolutorSecretos(entorno, null, new PropiedadesSecretos(null, 1));

    @Test
    void resuelveVariablesDeEntorno() {
        assertThat(resolutor.resolver("env:CLAVE_DEMO")).isEqualTo("valor-demo");
    }

    @Test
    void resuelveArchivosSinSaltosDeLinea(@TempDir Path carpeta) throws Exception {
        Path archivo = Files.writeString(carpeta.resolve("clave"), "desde-archivo\n");
        assertThat(resolutor.resolver("file:" + archivo)).isEqualTo("desde-archivo");
    }

    @Test
    void unaVariableInexistenteFallaSinInventarNada() {
        assertThatThrownBy(() -> resolutor.resolver("env:NO_EXISTE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NO_EXISTE");
    }

    @Test
    void rechazaReferenciasInvalidasODesconocidas() {
        assertThatThrownBy(() -> resolutor.resolver("texto-plano")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolutor.resolver("ftp:algo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolutor.resolver("env:")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolutor.resolver("vault:secreto/gym")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sinClaveMaestraNoSePuedenUsarSecretosCifrados() {
        assertThatThrownBy(() -> resolutor.guardarCifrado("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GYM_CLAVE_MAESTRA");
    }
}
