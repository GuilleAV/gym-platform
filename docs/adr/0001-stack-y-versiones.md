# ADR-0001 — Stack y versiones

- **Estado:** Propuesto (pendiente de aprobación del dueño del proyecto)
- **Fecha:** 23/11/2026

## Contexto
La especificación v2.0 fija "Java 21" y "MySQL 8". A noviembre de 2026:
- MySQL 8.0 llegó a fin de vida (abril 2026, última versión 8.0.46). La LTS vigente es 8.4.
- Spring Boot 3.5 perdió soporte OSS el 30/06/2026; la estable es 4.1.x.
- Angular 22 (junio 2026) es la versión activa, con LTS hasta junio 2028; Angular pasó a ciclo anual.
- Java 25 es la LTS más reciente.

## Decisión
| Componente | Versión |
|---|---|
| JDK | 25 LTS (Temurin) |
| Spring Boot | 4.1.x (BOM) |
| Spring Modulith | la alineada con Boot 4.1 |
| MySQL | 8.4 LTS |
| Flyway | la gestionada por el BOM de Spring Boot |
| Angular | 22.x |
| Node.js | LTS soportada por Angular 22 |
| Tests | JUnit 5, Testcontainers (mysql:8.4), Playwright para e2e |

## Consecuencias
- Más años de parches de seguridad sin migraciones mayores tempranas.
- Algunas guías de internet están escritas para Boot 3 / Angular ≤ 20: usar documentación actualizada (MCP Context7).
- **Revisar** este ADR cada 6 meses o cuando una versión entre en fin de soporte.
