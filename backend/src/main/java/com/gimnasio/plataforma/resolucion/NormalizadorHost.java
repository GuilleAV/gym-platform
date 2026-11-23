package com.gimnasio.plataforma.resolucion;

import java.net.IDN;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lleva el host de un request a la forma en que está guardado en {@code gimnasio_dominio}.
 *
 * <p>Los navegadores y proxies mandan el host de muchas formas: {@code Gym-Norte.com},
 * {@code gym-norte.com.} (con punto final, válido en DNS), {@code gimnasio-ñandú.com} (con acentos).
 * Si no normalizamos, el mismo gimnasio tendría varias "identidades" y un atacante podría jugar con
 * esas variantes. Todo lo que no sea un nombre de host válido se rechaza (Optional vacío).</p>
 */
final class NormalizadorHost {

    /** Mismo formato que el CHECK ck_dominio_host de la base (V1). */
    private static final Pattern HOST_VALIDO = Pattern.compile("^[a-z0-9-]+(\\.[a-z0-9-]+)*$");
    private static final int LARGO_MAXIMO = 253;

    private NormalizadorHost() {
    }

    static Optional<String> normalizar(String host) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        String h = host.strip();
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        try {
            // IDN.toASCII: "ñandú" -> "xn--and-6ma2c" (punycode). USE_STD3_ASCII_RULES rechaza
            // caracteres que no pueden ir en un nombre de host (espacios, "_", "/", ":", etc.).
            h = IDN.toASCII(h, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (h.isEmpty() || h.length() > LARGO_MAXIMO || !HOST_VALIDO.matcher(h).matches()) {
            return Optional.empty();
        }
        return Optional.of(h);
    }
}
