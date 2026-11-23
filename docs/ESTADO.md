# Estado del proyecto gym-platform

> **Leer esto primero al empezar cada conversación nueva.** Se actualiza al cerrar cada sesión de trabajo.

**Última actualización:** 23/11/2026 · **Fase actual:** antes de la Fase 0 (todavía no hay código)

---

## Hecho

- [x] Revisión técnica de la especificación v2.0 → `docs/revision/2026-11-23-revision-especificacion-v2.md` (también subida al Project "Gimnasio").
- [x] Repo git local en `C:\Gimnasio` (rama `main`, primer commit `5b8b29f`).
- [x] Estructura base: README, CLAUDE.md, .gitignore, .gitattributes, .editorconfig, docs/adr, docs/setup.
- [x] ADR-0001 (stack y versiones), en estado **Propuesto**.
- [x] MCP **Context7** conectado (documentación actualizada de librerías).

## Pendiente del usuario

- [ ] **Confirmar el stack:** Java 25 LTS, Spring Boot 4.1, MySQL 8.4 LTS, Angular 22. Si se aprueba, ADR-0001 pasa a **Aceptado**.
- [ ] **Decidir el MVP:** ¿pasar el "comercial mínimo + caja de recepción" a la Fase 1? (ver revisión §2.1).
- [ ] **Copiar los 3 `.docx` originales** a `docs/especificacion/`.
- [ ] **Crear el repo privado `gym-platform` en GitHub** (vacío, sin README) y hacer:
  ```powershell
  cd C:\Gimnasio
  git remote add origin https://github.com/GuilleAV/gym-platform.git
  git push -u origin main
  ```
- [ ] **MCP Engram:** instalado (`%USERPROFILE%\go\bin\engram.exe`) pero **todavía no aparece en Claude**.
  - Claude Desktop está instalado desde la Microsoft Store (MSIX), así que su configuración está en
    `%LOCALAPPDATA%\Packages\Claude_<id>\LocalCache\Roaming\Claude\claude_desktop_config.json`.
  - Ya se escribió la entrada `engram` (command = ruta al exe, args = `["mcp"]`).
  - Próxima verificación: probar en una conversación nueva después de reiniciar Claude por completo. Si no aparece, revisar el contenido del JSON y la hora de inicio del proceso Claude.

## Decisiones abiertas (bloquean la Fase 0, ver revisión §3)

| # | Decisión | Propuesta | ADR |
|---|---|---|---|
| 1 | Dónde viven las sesiones | Spring Session JDBC en la base de cada tenant | 0002 |
| 2 | Gestor de secretos | Variables de entorno / Docker secrets ahora; Vault en SaaS productivo | 0002 |
| 3 | Usuario de BD de aprovisionamiento | Credencial separada, solo para el job de alta | 0002 |
| 4 | Pools por tenant | HikariCP de 3 a 5 conexiones, mínimo 0 y desalojo de inactivos; revisar a ~50 tenants | 0002 |
| 5 | Rol del superadmin | Plantillas desde la plataforma + "sesión de soporte" auditada dentro del tenant | 0003 |

## Próximo paso

**Fase 0 — Paso 0.1:** `infra/docker-compose.yml` con MySQL 8.4, MinIO y Mailpit, más `.env.example` y verificación de las herramientas instaladas (Docker Desktop, JDK 25, Git).

Plan completo de la Fase 0:

| Paso | Contenido |
|---|---|
| 0.1 | docker-compose (MySQL 8.4, MinIO, Mailpit) |
| 0.2 | Esqueleto Spring Boot 4.1 + Spring Modulith + CI en GitHub Actions |
| 0.3 | Base `gym_platform` con Flyway (gimnasio, dominio, conexión) |
| 0.4 | Resolución del tenant (dominio → base), con limpieza del contexto |
| 0.5 | Aprovisionamiento idempotente de gym_001 y gym_002 |
| 0.6 | Spring Security: Argon2id, sesión en cookie, CSRF |
| 0.7 | Suite de aislamiento entre tenants (rompe el build si falla) |

## Entorno

- Windows. Proyecto en `C:\Gimnasio`.
- GitHub: usuario `GuilleAV`.
- Identidad git del repo: `GuilleAV <GuilleAV@users.noreply.github.com>`.
