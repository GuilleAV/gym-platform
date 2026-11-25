package com.gimnasio.identidad.mfa;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Códigos TOTP (RFC 6238): los mismos que muestran Google Authenticator, Microsoft Authenticator, etc.
 *
 * <p><b>Cómo funciona:</b> el servidor y el celular comparten un secreto. Cada 30 segundos ("paso"),
 * los dos calculan HMAC-SHA1(secreto, número de paso) y lo reducen a 6 dígitos. Como usan la misma
 * hora y el mismo secreto, llegan al mismo número sin comunicarse: por eso la app funciona sin internet.</p>
 *
 * <p>SHA-1 está roto para firmas digitales, pero no para HMAC: es lo que usan todas las apps de
 * códigos y lo que exige la compatibilidad con ellas.</p>
 *
 * <p>Clase pura (sin base ni Spring): se prueba con los vectores oficiales del RFC 6238.</p>
 */
public final class Totp {

    public static final int DIGITOS = 6;
    public static final int PERIODO_SEGUNDOS = 30;
    /** Pasos aceptados antes y después del actual: ±30 s para relojes un poco desfasados. */
    static final int TOLERANCIA_PASOS = 1;
    /** 160 bits: el largo recomendado por el RFC 4226 para HMAC-SHA1. */
    static final int LARGO_SECRETO_BYTES = 20;

    private static final String ALFABETO_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom AZAR = new SecureRandom();

    private Totp() {
    }

    public static byte[] nuevoSecreto() {
        byte[] secreto = new byte[LARGO_SECRETO_BYTES];
        AZAR.nextBytes(secreto);
        return secreto;
    }

    /** Número de paso de 30 s para un instante (milisegundos desde 1970, UTC). */
    public static long paso(long epochMilis) {
        return Math.floorDiv(epochMilis / 1000, PERIODO_SEGUNDOS);
    }

    /** El código de {@code digitos} cifras para un paso (RFC 4226 §5.3, "truncamiento dinámico"). */
    public static String codigo(byte[] secreto, long paso, int digitos) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA1");
            hmac.init(new SecretKeySpec(secreto, "HmacSHA1"));
            byte[] h = hmac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(paso).array());
            int desplazamiento = h[h.length - 1] & 0x0f;
            int binario = ((h[desplazamiento] & 0x7f) << 24) | ((h[desplazamiento + 1] & 0xff) << 16)
                    | ((h[desplazamiento + 2] & 0xff) << 8) | (h[desplazamiento + 3] & 0xff);
            int modulo = (int) Math.pow(10, digitos);
            return String.format(Locale.ROOT, "%0" + digitos + "d", binario % modulo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("La JVM no tiene HmacSHA1", e); // obligatorio en toda JVM
        }
    }

    /**
     * Verifica un código de 6 dígitos contra el paso actual y los vecinos (±1).
     *
     * @return el paso que coincidió (para impedir que se reuse), o -1 si no coincide ninguno.
     */
    public static long verificar(byte[] secreto, String codigo, long ahoraMilis) {
        if (codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) {
            return -1;
        }
        long actual = paso(ahoraMilis);
        byte[] recibido = codigo.getBytes(StandardCharsets.US_ASCII);
        for (long p = actual - TOLERANCIA_PASOS; p <= actual + TOLERANCIA_PASOS; p++) {
            // Comparación en tiempo constante: no revela cuántos dígitos acertó por el tiempo de respuesta.
            if (MessageDigest.isEqual(codigo(secreto, p, DIGITOS).getBytes(StandardCharsets.US_ASCII), recibido)) {
                return p;
            }
        }
        return -1;
    }

    /**
     * URI que se convierte en el código QR que escanea la app ("Key Uri Format" de Google Authenticator).
     *
     * @param emisor nombre del gimnasio: aparece en la app para saber de qué cuenta es el código
     * @param cuenta correo del usuario
     */
    public static String uri(String emisor, String cuenta, byte[] secreto) {
        String etiqueta = codificar(emisor) + ":" + codificar(cuenta);
        return "otpauth://totp/" + etiqueta + "?secret=" + base32(secreto) + "&issuer=" + codificar(emisor)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PERIODO_SEGUNDOS;
    }

    /** Base32 sin relleno (RFC 4648): el formato en que las apps esperan el secreto. */
    public static String base32(byte[] datos) {
        StringBuilder resultado = new StringBuilder((datos.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                resultado.append(ALFABETO_BASE32.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            resultado.append(ALFABETO_BASE32.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return resultado.toString();
    }

    /** Inverso de {@link #base32}. Ignora espacios, guiones, relleno y mayúsculas/minúsculas. */
    public static byte[] desdeBase32(String texto) {
        String limpio = texto.replaceAll("[\\s=-]", "").toUpperCase(Locale.ROOT);
        ByteBuffer salida = ByteBuffer.allocate(limpio.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpio.toCharArray()) {
            int valor = ALFABETO_BASE32.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Carácter inválido en Base32");
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return salida.array();
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
