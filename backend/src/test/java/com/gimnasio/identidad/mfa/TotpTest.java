package com.gimnasio.identidad.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unitario: el cálculo TOTP da EXACTAMENTE lo mismo que las apps. Se usan los vectores de prueba
 * oficiales del RFC 6238 (Apéndice B, SHA-1): si este test pasa, Google Authenticator va a coincidir.
 */
class TotpTest {

    /** Secreto del RFC 6238: los 20 bytes ASCII de "12345678901234567890". */
    private static final byte[] SECRETO_RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest(name = "t = {0} s → {1}")
    @CsvSource({
            "59,          94287082",
            "1111111109,  07081804",
            "1111111111,  14050471",
            "1234567890,  89005924",
            "2000000000,  69279037",
            "20000000000, 65353130"
    })
    void vectoresOficialesDelRfc6238(long segundos, String esperado) {
        assertThat(Totp.codigo(SECRETO_RFC, Totp.paso(segundos * 1000), 8)).isEqualTo(esperado);
    }

    @Test
    void conSeisDigitosSonLasUltimasSeisCifras() {
        assertThat(Totp.codigo(SECRETO_RFC, Totp.paso(59_000), 6)).isEqualTo("287082");
    }

    @Test
    void aceptaElPasoActualYLosVecinosPeroNoMasLejos() {
        long ahora = 1_234_567_890_000L;
        long paso = Totp.paso(ahora);
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso, 6), ahora)).isEqualTo(paso);
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso - 1, 6), ahora)).isEqualTo(paso - 1);
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso + 1, 6), ahora)).isEqualTo(paso + 1);
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso - 3, 6), ahora)).as("hace 90 s").isEqualTo(-1);
    }

    @Test
    void rechazaCodigosMalFormados() {
        long ahora = 59_000;
        assertThat(Totp.verificar(SECRETO_RFC, null, ahora)).isEqualTo(-1);
        assertThat(Totp.verificar(SECRETO_RFC, "28708", ahora)).isEqualTo(-1);
        assertThat(Totp.verificar(SECRETO_RFC, "2870821", ahora)).isEqualTo(-1);
        assertThat(Totp.verificar(SECRETO_RFC, "28708a", ahora)).isEqualTo(-1);
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({"f, MY", "fo, MZXQ", "foo, MZXW6", "foob, MZXW6YQ", "fooba, MZXW6YTB", "foobar, MZXW6YTBOI"})
    void base32SegunElRfc4648SinRelleno(String texto, String esperado) {
        byte[] bytes = texto.getBytes(StandardCharsets.US_ASCII);
        assertThat(Totp.base32(bytes)).isEqualTo(esperado);
        assertThat(Totp.desdeBase32(esperado)).isEqualTo(bytes);
    }

    @Test
    void elSecretoNuevoTiene160BitsYEsDistintoCadaVez() {
        byte[] uno = Totp.nuevoSecreto();
        assertThat(uno).hasSize(20);
        assertThat(Totp.nuevoSecreto()).isNotEqualTo(uno);
    }

    @Test
    void laUriTieneElFormatoQueEsperanLasApps() {
        String uri = Totp.uri("Gym Norte", "ana@correo.com", SECRETO_RFC);
        assertThat(uri).startsWith("otpauth://totp/Gym%20Norte:ana%40correo.com?secret=" + Totp.base32(SECRETO_RFC))
                .contains("&issuer=Gym%20Norte", "&digits=6", "&period=30", "&algorithm=SHA1");
    }
}
