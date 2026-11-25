package com.gimnasio.plataforma.secretos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Convierte una referencia de secreto ({@code secret_ref}) en el secreto real (ADR-0002 §2).
 *
 * <table>
 *   <caption>Tipos de referencia</caption>
 *   <tr><td>{@code env:NOMBRE}</td><td>variable de entorno o propiedad de Spring</td></tr>
 *   <tr><td>{@code file:/ruta}</td><td>archivo (Docker secrets)</td></tr>
 *   <tr><td>{@code cifrado:ID}</td><td>tabla {@code secreto}, cifrado con la clave maestra</td></tr>
 *   <tr><td>{@code vault:ruta}</td><td>gestor externo (todavía no implementado)</td></tr>
 * </table>
 *
 * <p><b>Regla:</b> ningún mensaje de error ni log incluye el valor del secreto, solo la referencia.</p>
 */
@Component
public class ResolutorSecretos {

    private final Environment entorno;
    private final RepositorioSecretos repositorio;
    private final PropiedadesSecretos propiedades;
    private volatile CifradorSecretos cifrador;

    public ResolutorSecretos(Environment entorno, RepositorioSecretos repositorio, PropiedadesSecretos propiedades) {
        this.entorno = entorno;
        this.repositorio = repositorio;
        this.propiedades = propiedades;
    }

    public String resolver(String referencia) {
        if (referencia == null || !referencia.contains(":")) {
            throw new IllegalArgumentException("Referencia de secreto inválida");
        }
        String tipo = referencia.substring(0, referencia.indexOf(':'));
        String valor = referencia.substring(referencia.indexOf(':') + 1);
        if (valor.isBlank()) {
            throw new IllegalArgumentException("Referencia de secreto inválida: " + tipo + ":<vacío>");
        }
        return switch (tipo) {
            case "env" -> {
                String secreto = entorno.getProperty(valor);
                if (secreto == null || secreto.isBlank()) {
                    throw new IllegalStateException("No está definida la variable del secreto: " + valor);
                }
                yield secreto;
            }
            case "file" -> leerArchivo(Path.of(valor));
            case "cifrado" -> cifrador().descifrar(repositorio.leer(Long.parseLong(valor)));
            case "vault" -> throw new UnsupportedOperationException("Las referencias vault: todavía no están implementadas (ADR-0002)");
            default -> throw new IllegalArgumentException("Tipo de referencia de secreto desconocido: " + tipo);
        };
    }

    /** Cifra y guarda un secreto nuevo. Devuelve la referencia para guardar en el catálogo. */
    public String guardarCifrado(String secreto) {
        return "cifrado:" + repositorio.guardar(cifrador().cifrar(secreto));
    }

    /**
     * Cifra un dato para guardarlo en una columna (no en la tabla {@code secreto}). Devuelve
     * versión de la clave (4 bytes) + IV (12 bytes) + texto cifrado, todo junto en un solo arreglo.
     */
    public byte[] cifrarEmpaquetado(String dato) {
        SecretoCifrado cifrado = cifrador().cifrar(dato);
        return java.nio.ByteBuffer.allocate(Integer.BYTES + cifrado.iv().length + cifrado.cifrado().length)
                .putInt(cifrado.versionClave()).put(cifrado.iv()).put(cifrado.cifrado()).array();
    }

    /** Inverso de {@link #cifrarEmpaquetado}. Falla si el dato fue alterado o la clave no corresponde. */
    public String descifrarEmpaquetado(byte[] empaquetado) {
        if (empaquetado == null || empaquetado.length <= Integer.BYTES + 12) {
            throw new IllegalArgumentException("Dato cifrado inválido");
        }
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(empaquetado);
        int version = buffer.getInt();
        byte[] iv = new byte[12];
        buffer.get(iv);
        byte[] cifrado = new byte[buffer.remaining()];
        buffer.get(cifrado);
        return cifrador().descifrar(new SecretoCifrado(version, iv, cifrado));
    }

    /** Se crea recién cuando se usa: la app puede arrancar sin clave maestra si no maneja secretos cifrados. */
    private CifradorSecretos cifrador() {
        CifradorSecretos actual = cifrador;
        if (actual == null) {
            actual = CifradorSecretos.desdeBase64(propiedades.claveMaestra(), propiedades.versionClave());
            cifrador = actual;
        }
        return actual;
    }

    private static String leerArchivo(Path archivo) {
        try {
            return Files.readString(archivo).strip();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo del secreto: " + archivo);
        }
    }
}
