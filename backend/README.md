# backend

Spring Boot 4.1 (monolito modular) sobre Java 25. Versiones fijadas en
[ADR-0001](../docs/adr/0001-stack-y-versiones.md).

## Abrir en IntelliJ IDEA

*File → Open…* → seleccionar **`backend/pom.xml`** → *Open as Project* → *Trust Project*.
Si falta el JDK: *File → Project Structure → SDK → Add SDK → Download JDK → 25, Eclipse Temurin*.

## Comandos

```bash
# Una sola vez: generar el Maven Wrapper (fija la versión de Maven) y commitearlo
mvn -N wrapper:wrapper -Dmaven=3.9.14 -Dtype=only-script

./mvnw test      # tests unitarios (*Test.java), sin Docker
./mvnw verify    # + tests de integración (*IT.java) con MySQL real vía Testcontainers (requiere Docker)
```

En Windows (PowerShell) es `.\mvnw.cmd verify`.

## Tests actuales

| Test | Tipo | Qué verifica |
|---|---|---|
| `VersionesStackTest` | Unitario | Java 25, imagen MySQL exacta 8.4.x, y que `infra/docker-compose.yml` use la misma imagen |
| `ModularidadTest` | Unitario | Spring Modulith: ningún módulo usa internos de otro, no hay ciclos; genera diagramas en `target/spring-modulith-docs` |
| `CatalogoPlataformaIT` | Integración | Flyway crea `gym_platform`; `gp_app` no puede crear/borrar tablas, bases ni usuarios; dominios únicos y normalizados; nombres de base y referencias de secreto con formato seguro |
| `MonitoreoContextoIT` | Integración | Spring arranca con la base del catálogo y el monitor de disco queda activo |
| `NormalizadorHostTest` | Unitario | El mismo dominio escrito distinto (mayúsculas, punto final, acentos) da una sola identidad; rechaza puertos, rutas y basura |
| `ContextoGimnasioTest` | Unitario | El gimnasio actual existe solo dentro de su bloque (`ScopedValue`), incluso si hay excepciones; sin gimnasio se deniega |
| `ResolucionGimnasioIT` | Integración | Cada dominio responde con su gimnasio; desconocido, suspendido o dominio de baja → 404; ignora `gimnasioId` del cliente y `X-Forwarded-Host` |
| `CifradorSecretosTest` | Unitario | AES-256-GCM: recupera el secreto, IV distinto cada vez, detecta alteraciones, otra clave o versión no descifra |
| `ResolutorSecretosTest` | Unitario | Referencias `env:`/`file:`/`cifrado:`/`vault:`; errores sin mostrar el secreto |
| `AprovisionamientoIT` | Integración | Alta crea base + usuario por gimnasio, clave cifrada y distinta, idempotente, no deja robar dominios; **el usuario de un gimnasio no puede leer la base de otro ni cambiar estructura**; por HTTP cada dominio lee de su base |
| `ContraseniasTest` | Unitario | Argon2id con parámetros OWASP (19 MiB, t=2, p=1), sal aleatoria, largo 12–128 |
| `FiltroGimnasioDeLaSesionTest` | Unitario | Una sesión iniciada en un gimnasio se invalida si llega en un request de otro |
| `SesionIT` | Integración | Ingresar/consultar/salir; cookie `__Host-SESION` Secure+HttpOnly+SameSite sin Domain; sesión guardada en la base de su gimnasio; mismas respuestas para clave mala, correo inexistente o cuenta suspendida; CSRF obligatorio; **la cookie y los usuarios de un gimnasio no sirven en otro**; la base rechaza hashes que no sean Argon2id; limpieza de sesiones vencidas por gimnasio |
| `MysqlVersionIT` | Integración | Un MySQL real levantado con Testcontainers responde versión 8.4 |

## Ejecutar la aplicación en IntelliJ (perfil demo)

1. Docker con MySQL corriendo (ver `infra/README.md`) y el script `01-plataforma.sh` aplicado.
2. *Run → Edit Configurations → GimnasioApplication* → **Program arguments**: `--spring.profiles.active=demo`.
   Con ese perfil la app lee las claves de `C:\Gimnasio\.env` (nunca están en el repo) y, al arrancar,
   da de alta los gimnasios de `application-demo.yml` (idempotente: reiniciar no duplica nada).
3. Probar en el navegador:
   - http://gym-norte.localhost:8080/api/gimnasio/bienvenida → responde desde la base de Gym Norte
   - http://power-fit.localhost:8080/api/gimnasio/bienvenida → responde desde la base de Power Fit
   - http://localhost:8080/api/gimnasio → 404 (dominio no registrado)

## Integración continua

`.github/workflows/ci.yml` corre `./mvnw verify` (unitarios + integración con Testcontainers) y los
tests + build del frontend en cada PR a `main`. Dependabot (`.github/dependabot.yml`) abre PRs
semanales con actualizaciones y avisos de vulnerabilidades.
