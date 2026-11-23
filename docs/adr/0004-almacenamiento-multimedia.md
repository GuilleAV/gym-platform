# ADR-0004: Almacenamiento y procesamiento de multimedia

- **Estado:** Aceptado
- **Fecha:** 2026-11-23
- **Decisores:** Dueño del producto (decisión) + Claude (rol arquitecto)

## Contexto

El sistema guarda fotos (perfil, galería), videos de ejercicios y documentos de inscripción.
La especificación v2 proponía MinIO, pero **MinIO Community fue archivado en febrero de 2026**
(sin parches de seguridad). Se evaluaron SeaweedFS, RustFS, Garage y Cloudflare R2.

Volumen estimado del piloto (~100 alumnos): menos de 1 GB en fotos/documentos y ~6 GB en videos.

## Decisión

1. **Los archivos se guardan en el disco del mismo servidor** donde corre la aplicación,
   tanto en el SaaS como en la instalación local en gimnasios.
2. El acceso se hace **siempre a través de una interfaz** `AlmacenamientoArchivos`
   (guardar, leer, borrar, urlTemporal). La única implementación inicial es
   `DiscoLocalAlmacenamiento`. Ningún módulo usa `java.io.File`/`Path` del almacenamiento directamente.
3. **En la base se guardan claves, no URLs.** Ejemplo: `gym_001/alumnos/<uuid>/800.webp`.
   La URL se arma al mostrar (cambia según dominio, despliegue o si es privada y vence).
4. **Original + versiones livianas:**

   | Versión | Uso | Formato |
   |---|---|---|
   | Original (máx. 4096 px de lado, sin metadatos EXIF) | Archivo maestro, privado | El subido |
   | `320` / `800` / `1600` | Miniatura / celular / pantalla grande | WebP |
   | Video | Reproducción | MP4 H.264 720p (`faststart`) |

5. **Procesamiento en segundo plano** (outbox + worker): la subida responde al instante con estado
   `PROCESANDO`; un worker genera las versiones **de a una por vez** y pasa a `LISTO` o `ERROR`.
6. Tablas previstas (módulo multimedia, fase 1): `media` (gimnasio, dueño, tipo, clave original,
   estado, tipo real del archivo, bytes, hash SHA-256) y `media_variante` (media, nombre, clave,
   ancho, alto, bytes).

### Reglas de seguridad (no negociables)

- **Fuera de la carpeta web.** Nunca se sirven por su ruta física: pasan por un endpoint que
  verifica sesión, tenant y permiso sobre el recurso, o por un token temporal firmado de vida corta.
- **Nombres generados por el sistema** (UUID). Nunca se usa el nombre que envía el usuario
  (evita *path traversal*: `../../`). La ruta se arma siempre desde el tenant de la **sesión**.
- **Una carpeta por gimnasio** (`<raíz>/gym_001/...`). Nada de supuestos sobre gimnasios concretos.
- **Validación del tipo real** por contenido (magic bytes), no por extensión. Límites de tamaño por tipo
  (propuesta: foto 20 MB, documento 10 MB, video 500 MB) y de píxeles (anti "bomba de descompresión").
- **Re-codificar las imágenes y borrar EXIF** (puede contener la ubicación GPS del alumno).
- Documentos de inscripción y salud: **privados siempre**, nunca en una galería pública.
- **Cuota de almacenamiento por gimnasio** configurable (requisito de escalabilidad de la spec).

### Reglas de operación (hardware)

- **Carpeta en un volumen/disco separado** del sistema operativo y de MySQL
  (ej. `/srv/gym-platform/media` en Linux). Si los videos llenan el disco, que no se caiga la base.
- **Backup diario de la carpeta de archivos fuera del servidor**, junto con el de la base.
  Sin esto, ante una falla se recupera la base pero no las fotos. La restauración se prueba.
- **Alerta de disco** al 70 % y al 85 % de uso.

## Alternativas consideradas

| Alternativa | Por qué no (por ahora) |
|---|---|
| MinIO | Archivado en feb. 2026: sin parches de seguridad. |
| SeaweedFS | Muy bueno con varios discos/servidores; con uno solo agrega un servicio a operar sin ganar seguridad ni velocidad. |
| RustFS | Reemplazo directo de MinIO, pero 1.0 recién en sept. 2026: poco recorrido. |
| Garage | Liviano, pero soporta menos funciones de S3. |
| Cloudflare R2 / S3 | Mejor durabilidad y escala horizontal, pero suma un proveedor y una cuenta externa. Queda como camino de migración. |

## Consecuencias

**Positivas**
- La opción más simple: sin servicios extra ni cuentas externas. Costo cero.
- Mismo mecanismo en SaaS e instalación local.

**Negativas / deuda asumida**
- **La aplicación queda "con estado":** mientras los archivos vivan en el disco, no se pueden correr
  dos instancias de la app en servidores distintos (escalado horizontal).
- La durabilidad depende de nuestros backups: un disco roto sin backup = archivos perdidos.
- Hay que vigilar el espacio en disco.

**Camino de migración (por qué es barato):** como el resto del sistema solo conoce la interfaz y en la
base hay claves (no URLs), migrar a un almacenamiento S3 (R2, SeaweedFS, etc.) es: implementar
`S3Almacenamiento`, copiar los archivos manteniendo las mismas claves (por ejemplo con `rclone`) y
cambiar una línea de configuración. Las tablas no se tocan.

## Cuándo revisar esta decisión (umbral)

- Se necesita **más de una instancia** de la aplicación (alta disponibilidad o carga).
- Los archivos superan **~200 GB por servidor**, o el disco pasa del **70 % de forma sostenida**.
- El backup diario de archivos tarda más que la ventana disponible.
- Un requisito legal obliga a una ubicación o certificación específica de los datos ⚖️ (consultar con el abogado).
