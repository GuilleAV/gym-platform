package com.gimnasio.inscripcion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lo que completa la persona (o la recepción) en la ficha (ADR-0007 §4). Los datos del responsable solo
 * se exigen si el alumno es menor.
 *
 * <p>Dos niveles de validación: {@link #erroresDeFormato()} para guardar un borrador (se puede dejar
 * incompleto, pero no con basura) y {@link #erroresParaEnviar(boolean)} para enviarla (completa).</p>
 */
public record DatosFicha(String dni, String direccion, String emergenciaNombre, String emergenciaTelefono, String objetivo,
        String responsableNombre, String responsableDni, String responsableTelefono, String responsableEmail,
        String responsableVinculo) {

    public static final Set<String> OBJETIVOS = Set.of("BAJAR_DE_PESO", "FUERZA", "MUSCULACION", "SALUD", "RENDIMIENTO", "OTRO");
    public static final Set<String> VINCULOS = Set.of("MADRE", "PADRE", "TUTOR");

    private static final Pattern DNI = Pattern.compile("^[0-9A-Za-z.]{6,15}$");
    private static final Pattern TELEFONO = Pattern.compile("^\\+?[0-9 ()-]{6,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public static DatosFicha vacia() {
        return new DatosFicha(null, null, null, null, null, null, null, null, null, null);
    }

    /** Sin espacios de más; vacíos → null; códigos en mayúsculas; correo en minúsculas. */
    public DatosFicha normalizados() {
        return new DatosFicha(limpiar(dni), limpiar(direccion), limpiar(emergenciaNombre), limpiar(emergenciaTelefono),
                mayusculas(objetivo), limpiar(responsableNombre), limpiar(responsableDni), limpiar(responsableTelefono),
                responsableEmail == null || responsableEmail.isBlank() ? null : responsableEmail.strip().toLowerCase(Locale.ROOT),
                mayusculas(responsableVinculo));
    }

    /** Para un borrador: lo que vino tiene que tener buen formato, pero puede faltar. */
    public List<String> erroresDeFormato() {
        DatosFicha d = normalizados();
        List<String> errores = new ArrayList<>();
        formato(errores, "dni", d.dni, DNI);
        largo(errores, "direccion", d.direccion, 3, 200);
        largo(errores, "emergenciaNombre", d.emergenciaNombre, 3, 120);
        formato(errores, "emergenciaTelefono", d.emergenciaTelefono, TELEFONO);
        if (d.objetivo != null && !OBJETIVOS.contains(d.objetivo)) {
            errores.add("objetivo: uno de " + String.join(", ", OBJETIVOS.stream().sorted().toList()));
        }
        largo(errores, "responsableNombre", d.responsableNombre, 3, 120);
        formato(errores, "responsableDni", d.responsableDni, DNI);
        formato(errores, "responsableTelefono", d.responsableTelefono, TELEFONO);
        formato(errores, "responsableEmail", d.responsableEmail, EMAIL);
        if (d.responsableVinculo != null && !VINCULOS.contains(d.responsableVinculo)) {
            errores.add("responsableVinculo: MADRE, PADRE o TUTOR");
        }
        return errores;
    }

    /** Para enviar: además del formato, que esté completa (y con responsable si es menor). */
    public List<String> erroresParaEnviar(boolean esMenor) {
        DatosFicha d = normalizados();
        List<String> errores = new ArrayList<>(erroresDeFormato());
        obligatorio(errores, "dni", d.dni);
        obligatorio(errores, "direccion", d.direccion);
        obligatorio(errores, "emergenciaNombre", d.emergenciaNombre);
        obligatorio(errores, "emergenciaTelefono", d.emergenciaTelefono);
        obligatorio(errores, "objetivo", d.objetivo);
        if (esMenor) {
            obligatorio(errores, "responsableNombre", d.responsableNombre);
            obligatorio(errores, "responsableDni", d.responsableDni);
            obligatorio(errores, "responsableTelefono", d.responsableTelefono);
            obligatorio(errores, "responsableVinculo", d.responsableVinculo);
        }
        return errores;
    }

    private static void obligatorio(List<String> errores, String campo, String valor) {
        if (valor == null && errores.stream().noneMatch(e -> e.startsWith(campo + ":"))) {
            errores.add(campo + ": obligatorio");
        }
    }

    private static void formato(List<String> errores, String campo, String valor, Pattern patron) {
        if (valor != null && !patron.matcher(valor).matches()) {
            errores.add(campo + ": formato inválido");
        }
    }

    private static void largo(List<String> errores, String campo, String valor, int minimo, int maximo) {
        if (valor != null && (valor.length() < minimo || valor.length() > maximo)) {
            errores.add(campo + ": entre " + minimo + " y " + maximo + " caracteres");
        }
    }

    private static String limpiar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.strip().replaceAll("\\s+", " ");
    }

    private static String mayusculas(String texto) {
        String limpio = limpiar(texto);
        return limpio == null ? null : limpio.toUpperCase(Locale.ROOT);
    }
}
