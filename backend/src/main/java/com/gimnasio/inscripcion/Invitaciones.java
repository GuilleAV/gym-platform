package com.gimnasio.inscripcion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.gimnasio.notificaciones.Correo;
import com.gimnasio.plataforma.BaseDelGimnasio;
import com.gimnasio.plataforma.ContextoGimnasio;

/**
 * Tokens de invitación para completar la ficha (ADR-0007 §6): 256 bits aleatorios, se guarda solo el
 * SHA-256, de un solo uso, válidos 7 días.
 */
@Component
class Invitaciones {

    static final Duration VIGENCIA = Duration.ofDays(7);
    static final Duration ESPERA_ENTRE_REENVIOS = Duration.ofMinutes(1);
    private static final int BYTES_TOKEN = 32;

    private final BaseDelGimnasio base;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    Invitaciones(BaseDelGimnasio base, Clock reloj) {
        this.base = base;
        this.reloj = reloj;
    }

    /** Anula las invitaciones anteriores de la inscripción y crea una nueva. Devuelve el token EN CLARO. */
    String nueva(long inscripcionId) {
        byte[] bytes = new byte[BYTES_TOKEN];
        azar.nextBytes(bytes);
        // Base64 "URL-safe" sin relleno: se puede poner en un enlace sin escapar nada.
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long ahora = reloj.millis();
        base.jdbc().update("UPDATE invitacion SET usada_ms = ? WHERE inscripcion_id = ? AND usada_ms IS NULL", ahora,
                inscripcionId);
        base.jdbc().update("INSERT INTO invitacion (inscripcion_id, hash_token, creada_ms, expira_ms) VALUES (?, ?, ?, ?)",
                inscripcionId, hash(token), ahora, ahora + VIGENCIA.toMillis());
        return token;
    }

    /** ¿Pasó el tiempo mínimo desde la última invitación de esta inscripción? */
    boolean puedeReenviar(long inscripcionId) {
        Long ultima = base.jdbc().queryForObject("SELECT MAX(creada_ms) FROM invitacion WHERE inscripcion_id = ?",
                Long.class, inscripcionId);
        return ultima == null || reloj.millis() - ultima >= ESPERA_ENTRE_REENVIOS.toMillis();
    }

    /**
     * El enlace lleva el token en el FRAGMENTO (#t=...): el navegador nunca lo manda al servidor, así que no
     * queda en logs de acceso, proxies ni en el Referer.
     */
    static Correo correo(String para, String nombre, String urlBase, String token) {
        String gimnasio = ContextoGimnasio.actual().nombre();
        String enlace = urlBase + "/invitacion#t=" + token;
        String texto = """
                Hola %s,

                Te inscribimos en %s. Para terminar, completá tu ficha en este enlace:

                %s

                El enlace vence en %d días y sirve una sola vez. No necesitás inventar ninguna contraseña:
                para entrar después, te vamos a mandar un código a este correo.

                Si no te inscribiste en %s, ignorá este mensaje.
                """.formatted(nombre, gimnasio, enlace, VIGENCIA.toDays(), gimnasio);
        return new Correo(para, "Completá tu inscripción en " + gimnasio, texto);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }
}
