package com.gimnasio.plataforma;

import org.springframework.stereotype.Component;

import com.gimnasio.plataforma.secretos.ResolutorSecretos;

/**
 * Cifrado de datos sensibles para guardar en columnas de la base (API pública de plataforma).
 *
 * <p>Usa la misma clave maestra y el mismo algoritmo (AES-256-GCM) que los secretos de conexión
 * (ADR-0002). Uso típico: el secreto TOTP de cada usuario (ADR-0005). Un volcado de la base sin la
 * clave maestra no permite leer estos datos.</p>
 *
 * <p>Existe para que otros módulos no dependan del paquete interno {@code secretos}: Spring Modulith
 * hace fallar el build si lo usan directamente.</p>
 */
@Component
public class CifradoDeDatos {

    private final ResolutorSecretos secretos;

    public CifradoDeDatos(ResolutorSecretos secretos) {
        this.secretos = secretos;
    }

    public byte[] cifrar(String dato) {
        return secretos.cifrarEmpaquetado(dato);
    }

    public String descifrar(byte[] cifrado) {
        return secretos.descifrarEmpaquetado(cifrado);
    }
}
