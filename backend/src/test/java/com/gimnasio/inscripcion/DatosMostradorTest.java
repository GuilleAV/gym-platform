package com.gimnasio.inscripcion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Unitario: validación y limpieza de la preinscripción del mostrador. */
class DatosMostradorTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 24);

    private static DatosMostrador valido() {
        return new DatosMostrador("Ana", "Pérez", "ana@correo.com", "+54 11 5555-1234", LocalDate.of(1990, 5, 10));
    }

    @Test
    void conDatosCompletosNoHayErrores() {
        assertThat(valido().errores(HOY)).isEmpty();
    }

    @Test
    void elCorreoEsOpcional() {
        assertThat(new DatosMostrador("Ana", "Pérez", null, "1155551234", LocalDate.of(1990, 5, 10)).errores(HOY)).isEmpty();
        assertThat(new DatosMostrador("Ana", "Pérez", "  ", "1155551234", LocalDate.of(1990, 5, 10)).normalizados().email()).isNull();
    }

    @Test
    void detectaCadaCampoInvalido() {
        DatosMostrador mal = new DatosMostrador(" ", "", "no-es-correo", "abc", HOY.plusDays(1));

        assertThat(mal.errores(HOY)).hasSize(5).anySatisfy(e -> assertThat(e).startsWith("nombre"))
                .anySatisfy(e -> assertThat(e).startsWith("apellido")).anySatisfy(e -> assertThat(e).startsWith("email"))
                .anySatisfy(e -> assertThat(e).startsWith("celular")).anySatisfy(e -> assertThat(e).startsWith("fechaNacimiento"));
    }

    @Test
    void unaFechaDeNacimientoDeHace150AniosNoEsRazonable() {
        DatosMostrador d = new DatosMostrador("Ana", "Pérez", null, "1155551234", HOY.minusYears(150));
        assertThat(d.errores(HOY)).singleElement().asString().startsWith("fechaNacimiento");
    }

    @Test
    void normalizaEspaciosYPasaElCorreoAMinusculas() {
        DatosMostrador d = new DatosMostrador("  Ana   María ", " Pérez ", " Ana@Correo.COM ", " 1155551234 ", HOY.minusYears(30))
                .normalizados();
        assertThat(d.nombre()).isEqualTo("Ana María");
        assertThat(d.apellido()).isEqualTo("Pérez");
        assertThat(d.email()).isEqualTo("ana@correo.com");
        assertThat(d.celular()).isEqualTo("1155551234");
    }

    @Test
    void esMenorHastaElDiaQueCumple18() {
        DatosMostrador cumpleManiana = new DatosMostrador("Leo", "Gómez", null, "1155551234", HOY.minusYears(18).plusDays(1));
        DatosMostrador cumpleHoy = new DatosMostrador("Leo", "Gómez", null, "1155551234", HOY.minusYears(18));
        assertThat(cumpleManiana.esMenorEn(HOY)).isTrue();
        assertThat(cumpleHoy.esMenorEn(HOY)).isFalse();
    }
}
