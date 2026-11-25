package com.gimnasio.inscripcion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Preinscripción cargada en el mostrador (ADR-0007 §4): lo mínimo, sin datos sensibles.
 *
 * @param email opcional: quien no usa correo queda inscripto, pero sin cuenta para la app
 */
public record DatosMostrador(String nombre, String apellido, String email, String celular, LocalDate fechaNacimiento) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CELULAR = Pattern.compile("^\\+?[0-9 ()-]{6,20}$");
    private static final int EDAD_MAXIMA = 110;
    static final int MAYORIA_DE_EDAD = 18;

    /** Los mismos datos, limpios: sin espacios de más y el correo en minúsculas (o null si vino vacío). */
    public DatosMostrador normalizados() {
        return new DatosMostrador(limpiar(nombre), limpiar(apellido),
                email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT),
                limpiar(celular), fechaNacimiento);
    }

    /** Lista de errores de validación (vacía si está todo bien). Se valida contra la fecha del gimnasio. */
    public List<String> errores(LocalDate hoy) {
        List<String> errores = new ArrayList<>();
        if (nombre == null || nombre.isBlank() || nombre.strip().length() > 80) {
            errores.add("nombre: obligatorio, hasta 80 caracteres");
        }
        if (apellido == null || apellido.isBlank() || apellido.strip().length() > 80) {
            errores.add("apellido: obligatorio, hasta 80 caracteres");
        }
        if (email != null && !email.isBlank() && (email.strip().length() > 254 || !EMAIL.matcher(email.strip()).matches())) {
            errores.add("email: formato inválido");
        }
        if (celular == null || !CELULAR.matcher(celular.strip()).matches()) {
            errores.add("celular: entre 6 y 20 dígitos (puede tener +, espacios, guiones o paréntesis)");
        }
        if (fechaNacimiento == null || fechaNacimiento.isAfter(hoy) || fechaNacimiento.isBefore(hoy.minusYears(EDAD_MAXIMA))) {
            errores.add("fechaNacimiento: obligatoria y razonable");
        }
        return errores;
    }

    public boolean esMenorEn(LocalDate hoy) {
        return fechaNacimiento.plusYears(MAYORIA_DE_EDAD).isAfter(hoy);
    }

    private static String limpiar(String texto) {
        return texto == null ? null : texto.strip().replaceAll("\\s+", " ");
    }
}
