package com.gimnasio.inscripcion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unitario: el borrador acepta datos incompletos pero no mal formados; el envío exige la ficha completa. */
class DatosFichaTest {

    private static final DatosFicha COMPLETA_ADULTO = new DatosFicha("30123456", "Av. Siempre Viva 742", "Marta Pérez",
            "1155550000", "fuerza", null, null, null, null, null);

    @Test
    void unBorradorPuedeEstarVacio() {
        assertThat(DatosFicha.vacia().erroresDeFormato()).isEmpty();
    }

    @Test
    void unBorradorConBasuraSeRechaza() {
        DatosFicha mal = new DatosFicha("x", "ab", null, "abc", "NADAR", null, null, null, "no-mail", "PRIMO");
        assertThat(mal.erroresDeFormato()).extracting(e -> e.substring(0, e.indexOf(':')))
                .containsExactlyInAnyOrder("dni", "direccion", "emergenciaTelefono", "objetivo", "responsableEmail",
                        "responsableVinculo");
    }

    @Test
    void unAdultoNoNecesitaResponsable() {
        assertThat(COMPLETA_ADULTO.erroresParaEnviar(false)).isEmpty();
    }

    @Test
    void unMenorSinResponsableNoSePuedeEnviar() {
        assertThat(COMPLETA_ADULTO.erroresParaEnviar(true)).extracting(e -> e.substring(0, e.indexOf(':')))
                .containsExactlyInAnyOrder("responsableNombre", "responsableDni", "responsableTelefono", "responsableVinculo");
    }

    @Test
    void paraEnviarFaltaLoObligatorio() {
        assertThat(DatosFicha.vacia().erroresParaEnviar(false)).hasSize(5);
    }

    @Test
    void normalizaCodigosAMayusculasYVaciosANull() {
        DatosFicha d = new DatosFicha(" 30123456 ", "  ", null, null, " salud ", null, null, null, " ", "madre").normalizados();
        assertThat(d.dni()).isEqualTo("30123456");
        assertThat(d.direccion()).isNull();
        assertThat(d.objetivo()).isEqualTo("SALUD");
        assertThat(d.responsableEmail()).isNull();
        assertThat(d.responsableVinculo()).isEqualTo("MADRE");
    }
}
