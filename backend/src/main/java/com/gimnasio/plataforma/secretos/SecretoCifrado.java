package com.gimnasio.plataforma.secretos;

/**
 * Un secreto ya cifrado, tal como se guarda en la tabla {@code secreto}.
 *
 * @param versionClave versión de la clave maestra con que se cifró.
 * @param iv           nonce de 12 bytes, distinto en cada cifrado.
 * @param cifrado      texto cifrado + tag de autenticación de GCM.
 */
public record SecretoCifrado(int versionClave, byte[] iv, byte[] cifrado) {
}
