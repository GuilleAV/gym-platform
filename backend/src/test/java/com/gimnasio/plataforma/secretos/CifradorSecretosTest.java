package com.gimnasio.plataforma.secretos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Unitario: AES-256-GCM oculta, autentica y no deja descifrar con otra clave. */
class CifradorSecretosTest {

    private static final byte[] CLAVE = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OTRA_CLAVE = "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.US_ASCII);

    private final CifradorSecretos cifrador = new CifradorSecretos(CLAVE, 1);

    @Test
    void loQueSeCifraSeRecuperaIgual() {
        SecretoCifrado cifrado = cifrador.cifrar("clave-de-la-base-ñ");
        assertThat(cifrador.descifrar(cifrado)).isEqualTo("clave-de-la-base-ñ");
    }

    @Test
    void elTextoCifradoNoContieneElSecreto() {
        SecretoCifrado cifrado = cifrador.cifrar("super-secreta");
        assertThat(new String(cifrado.cifrado(), StandardCharsets.ISO_8859_1)).doesNotContain("super-secreta");
    }

    @Test
    void cifrarDosVecesLoMismoDaResultadosDistintos() {
        SecretoCifrado a = cifrador.cifrar("igual");
        SecretoCifrado b = cifrador.cifrar("igual");
        assertThat(a.iv()).isNotEqualTo(b.iv());
        assertThat(a.cifrado()).isNotEqualTo(b.cifrado());
    }

    @Test
    void siAlguienModificaUnByteElDescifradoFalla() {
        SecretoCifrado cifrado = cifrador.cifrar("intacto");
        byte[] alterado = cifrado.cifrado().clone();
        alterado[0] ^= 1;
        assertThatThrownBy(() -> cifrador.descifrar(new SecretoCifrado(1, cifrado.iv(), alterado)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alterado");
    }

    @Test
    void conOtraClaveMaestraNoSePuedeDescifrar() {
        SecretoCifrado cifrado = cifrador.cifrar("privado");
        assertThatThrownBy(() -> new CifradorSecretos(OTRA_CLAVE, 1).descifrar(cifrado))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unSecretoDeOtraVersionDeClaveSeRechaza() {
        SecretoCifrado cifrado = cifrador.cifrar("v1");
        assertThatThrownBy(() -> new CifradorSecretos(CLAVE, 2).descifrar(cifrado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rotación");
    }

    @Test
    void laClaveMaestraDebeTener256Bits() {
        assertThatThrownBy(() -> new CifradorSecretos(new byte[16], 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CifradorSecretos.desdeBase64("", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GYM_CLAVE_MAESTRA");
    }
}
