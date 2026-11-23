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
| `GimnasioApplicationTests` | Unitario | El contexto de Spring arranca |
| `VersionesStackTest` | Unitario | Java 25, imagen MySQL exacta 8.4.x, y que `infra/docker-compose.yml` use la misma imagen |
| `ModularidadTest` | Unitario | Spring Modulith: ningún módulo usa internos de otro, no hay ciclos; genera diagramas en `target/spring-modulith-docs` |
| `MysqlVersionIT` | Integración | Un MySQL real levantado con Testcontainers responde versión 8.4 |

## Integración continua

`.github/workflows/ci.yml` corre `./mvnw verify` (unitarios + integración con Testcontainers) y los
tests + build del frontend en cada PR a `main`. Dependabot (`.github/dependabot.yml`) abre PRs
semanales con actualizaciones y avisos de vulnerabilidades.
