# CLAUDE.md — reglas del proyecto gym-platform

Responder siempre en español. El dueño del proyecto aprende Spring Boot y Angular: explicar el porqué de cada pieza importante.

## Fuente de verdad
- Especificación: `docs/especificacion/` (v2.0) + revisión `docs/revision/2026-11-23-revision-especificacion-v2.md`.
- Decisiones: `docs/adr/`. Toda decisión que limite el crecimiento se documenta con su umbral de revisión.

## Stack
Java 25 · Spring Boot 4.1 · Spring Modulith · MySQL 8.4 LTS · Flyway · Angular 22 PWA · Docker · Testcontainers.
Antes de usar una API de librería, consultar documentación actualizada (MCP Context7).

## Multi-tenant
- `gym_platform` = catálogo; una base por gimnasio con esquema común.
- Tenant = dominio registrado + sesión validada. Nunca `gimnasioId` del cliente. Ante duda, denegar.
- Limpiar el contexto de tenant al final de cada request y job (`finally`).
- Nada de supuestos sobre gym_001/gym_002 en el código: todo debe funcionar con N.

## Seguridad (siempre)
Argon2id · cookies HttpOnly/Secure/SameSite + CSRF · MFA para admins · autorización por operación y por recurso (IDOR) · consultas parametrizadas · sin secretos en repo/logs · sin datos personales/salud en logs · errores sin detalles internos · webhooks con firma + consulta + idempotencia.

## Tests (regla de oro)
Ningún cambio sin tests: unitarios de dominio, integración con Testcontainers MySQL (no H2), API (feliz, validación, sin auth, sin permiso, otro tenant), seguridad e idempotencia. Un bug se corrige empezando por un test que falla.

## Convenciones
- Ramas `feature/<tema>`, PR a `main`, commits en español con prefijo (`feat:`, `fix:`, `docs:`, `test:`, `chore:`).
- Migraciones Flyway: nunca editar una aplicada; crear una nueva.
- Dinero en `DECIMAL(12,2)` / `BigDecimal`. Fechas en UTC; zona por gimnasio.
- Ser honesto: si algo no se compiló o testeó, decirlo y dar el comando (`./mvnw verify`, `npm test`).

## Memoria (MCP Engram)
Guardar en Engram las decisiones, bugs resueltos y convenciones nuevas al cerrar cada sesión de trabajo; consultarlo al empezar.
