package com.gimnasio.plataforma.secretos;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifra y descifra secretos con AES-256-GCM (ADR-0002 §2).
 *
 * <p><b>Por qué GCM:</b> además de ocultar el dato, lo <i>autentica</i>: si alguien modifica un solo
 * byte del texto cifrado en la base, el descifrado falla en vez de devolver basura. Cada cifrado usa
 * un IV (nonce) aleatorio nuevo: cifrar dos veces la misma clave da resultados distintos.</p>
 *
 * <p>La versión de la clave maestra entra como "datos asociados" (AAD): un secreto cifrado con la
 * versión 1 no puede hacerse pasar por uno de la versión 2.</p>
 */
public final class CifradorSecretos {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int LARGO_IV = 12;
    private static final int LARGO_TAG_BITS = 128;
    private static final int LARGO_CLAVE = 32;

    private final SecretKeySpec clave;
    private final int version;
    private final SecureRandom azar = new SecureRandom();

    public CifradorSecretos(byte[] claveMaestra, int version) {
        if (claveMaestra == null || claveMaestra.length != LARGO_CLAVE) {
            throw new IllegalArgumentException("La clave maestra debe tener 256 bits (32 bytes)");
        }
        this.clave = new SecretKeySpec(claveMaestra.clone(), "AES");
        this.version = version;
    }

    public static CifradorSecretos desdeBase64(String claveBase64, int version) {
        if (claveBase64 == null || claveBase64.isBlank()) {
            throw new IllegalStateException(
                    "Falta la clave maestra: definí GYM_CLAVE_MAESTRA (32 bytes en Base64, ver .env.example)");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(claveBase64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("GYM_CLAVE_MAESTRA no es Base64 válido");
        }
        return new CifradorSecretos(bytes, version);
    }

    public SecretoCifrado cifrar(String texto) {
        byte[] iv = new byte[LARGO_IV];
        azar.nextBytes(iv);
        try {
            Cipher cifra = Cipher.getInstance(ALGORITMO);
            cifra.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(LARGO_TAG_BITS, iv));
            cifra.updateAAD(aad(version));
            return new SecretoCifrado(version, iv, cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el secreto", e);
        }
    }

    public String descifrar(SecretoCifrado secreto) {
        if (secreto.versionClave() != version) {
            throw new IllegalStateException("El secreto está cifrado con la versión " + secreto.versionClave()
                    + " de la clave maestra y la actual es " + version + ": falta re-cifrarlo (rotación)");
        }
        try {
            Cipher cifra = Cipher.getInstance(ALGORITMO);
            cifra.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(LARGO_TAG_BITS, secreto.iv()));
            cifra.updateAAD(aad(secreto.versionClave()));
            return new String(cifra.doFinal(secreto.cifrado()), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            // Sin detalles del dato: solo que no cierra.
            throw new IllegalStateException("No se pudo descifrar el secreto: clave maestra incorrecta o dato alterado");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el secreto", e);
        }
    }

    private static byte[] aad(int version) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(version).array();
    }
}
