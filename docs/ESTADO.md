# Estado del proyecto gym-platform

> **Leer esto primero al empezar cada conversación nueva.** Se actualiza al cerrar cada sesión de trabajo.

**Última actualización:** 23/11/2026 · **Fase actual:** Fase 0 — 0.2 a 0.5 mergeados en `main`; 0.6 en PR #8 (CI ✅); sigue 0.6b (MFA, límite de intentos, invitaciones) y 0.7

---

## Hecho

- [x] Revisión técnica de la especificación v2.0 → `docs/revision/2026-11-23-revision-especificacion-v2.md`.
- [x] Repo git local en `C:\Gimnasio`, estructura base, CLAUDE.md, ADRs, MCP Context7.
- [x] **Paso 0.1 — CERRADO (23/11/2026)**:
  - **ADR-0001** con versiones exactas: Spring Boot 4.1.2, Java 25 (Temurin 25.0.5.1), MySQL 8.4.13, Angular 22.3.0, Node 24 LTS (sigue *Propuesto*).
  - **ADR-0004** (Aceptado): fotos y videos en el **disco del mismo servidor**, detrás de la interfaz `AlmacenamientoArchivos`; originales + versiones WebP; claves (no URLs) en la base. MinIO descartado (archivado en feb. 2026).
  - `infra/docker-compose.yml`: MySQL 8.4.13 (127.0.0.1:**3307**) y Mailpit 1.34.0 (SMTP 1025, web 8025). Los dos **healthy** en Docker Desktop.
  - `backend/`: Boot 4.1.2 + Maven Enforcer + **aviso de disco al 85 %** (`com.gimnasio.monitoreo`: cada 15 min, correo y log, recordatorio cada 24 h, histéresis de 5 puntos). **31 tests unitarios pasan** en IntelliJ 2025.2.6.
  - `frontend/`: Angular 22.3.0 con Node 24 vía fnm. Tests, build y `npm start` funcionan en Windows.
  - Tests guardianes del compose: imágenes con versión exacta, puertos solo en 127.0.0.1, compose = pom.
  - Consola y correos en UTF-8 (`backend/.mvn/jvm.config`).

- [x] **Flujo de trabajo con CI (23/11/2026):** Claude trabaja en una copia del repo en la nube, sube ramas y
  lee la CI de GitHub Actions (Docker + Testcontainers) hasta dejarla en verde. El usuario hace `git pull`.
  **Nada se mergea a `main` sin el OK del usuario.**
- [x] **Paso 0.3 — PR #2 (CI ✅):** `gym_platform` con Flyway (`gimnasio`, `gimnasio_dominio`, `gimnasio_conexion`,
  `secreto`), script `infra/mysql/init/01-plataforma.sh` (usuarios `gp_app` solo DML y `gp_aprovisionador`).
- [x] **Paso 0.4 — PR #3 (CI ✅):** resolución del gimnasio por dominio (`FiltroResolucionGimnasio`,
  `ContextoGimnasio` con `ScopedValue`), 404 genérico si el dominio no existe, `GET /api/gimnasio`.
- [x] **Paso 0.5 — PR #4 (CI ✅):** alta automática idempotente (`ServicioAprovisionamiento`): base `gym_NNN`,
  usuario `gym_NNN_app` (solo DML sobre su base), clave cifrada AES-256-GCM (`GYM_CLAVE_MAESTRA`), pools HikariCP
  por gimnasio con cierre por inactividad (Caffeine), `BaseDelGimnasio`, perfil `demo`. 98 tests.
  - Bug aprendido: en `GRANT`, `_` en el nombre de la base es comodín → se escapa (`gym\_033`).
  - Pendiente técnico: Spring 7 no traduce el error 3819 (CHECK) a `DataIntegrityViolationException`; resolverlo
    cuando haya capa de API con validaciones (0.6+). Migrar automáticamente las bases existentes al arrancar.
- [x] **Paso 0.6 — PR #8 (CI ✅):** módulo `identidad`. Usuarios en la base de cada gimnasio (V2), Argon2id
  (OWASP: 19 MiB, t=2, p=1), Spring Session JDBC en la base del gimnasio actual, cookie `__Host-SESION`
  configurada en código (Boot 4 no aplicaba `server.servlet.session.cookie.*` a Spring Session: lo detectó
  SesionIT), CSRF SPA con `__Host-XSRF-TOKEN`, endpoints `/api/sesion/*`. Pantalla Angular mínima de login
  (proxy sin cambiar Host + `allowedHosts: .localhost`). 121 tests backend + 6 frontend.
  - Desvío menor del ADR-0002: la tabla de atributos se llama `sesion_ATTRIBUTES` (la impone Spring Session).
  - Pendiente 0.6b: MFA de admins (ADR-0003), límite de intentos, invitación por correo, vencimiento absoluto de 12 h.
- [x] **Mergeados en `main` (23/11/2026):** PRs #1 (0.2), #2 (0.3), #3 (0.4), #4 (0.5).
- [ ] **Dependabot:** #5 acciones (CI ✅), #6 Spring Boot 4.2 + Modulith 2.2 + Maven 3.10 (CI ✅, requiere
  actualizar ADR-0001), #7 TypeScript 7 (CI ❌: Angular 22 no lo soporta → cerrar e ignorar mayores de TS).
- [x] **Paso 0.2 — mergeado** (rama `feature/paso-0-2`, sin mergear):
  - Spring Modulith **2.1.1** (BOM en el pom) + `ModularidadTest`: `verify()` (límites y ciclos), guardia
    anti-"verificación en vacío" y diagramas PlantUML en `target/spring-modulith-docs`.
  - `.github/workflows/ci.yml`: backend (`./mvnw verify` con Testcontainers) y frontend (`npm ci`, tests, build)
    en paralelo, permisos mínimos, cancelación de corridas viejas, reportes si falla.
  - `.github/dependabot.yml`: maven, npm (Angular agrupado), github-actions y docker-compose (MySQL fijo en 8.4).
  - ADR-0001: Maven 3.9.14 y Spring Modulith 2.1.1 fijados.
  - **Falta:** generar el Maven Wrapper en IntelliJ, correr `verify` y (con el repo en GitHub) ver la CI en verde.

## Pendiente del usuario

> Las notas personales y de la PC están en `docs/privado/notas-personales.md` (fuera de Git).

- [ ] **Confirmar el stack:** Java 25 LTS, Spring Boot 4.1, MySQL 8.4 LTS, Angular 22. Si se aprueba, ADR-0001 pasa a **Aceptado**.
- [ ] **Decidir el MVP:** ¿pasar el "comercial mínimo + caja de recepción" a la Fase 1? (ver revisión §2.1).
- [ ] **Copiar los 3 `.docx` originales** a `docs/especificacion/`.
- [ ] **MCP Engram:** instalado pero todavía no aparece en Claude (detalle en las notas privadas).
- [ ] **Crear el repo privado `gym-platform` en GitHub** (vacío, sin README) y hacer:
  ```powershell
  cd C:\Gimnasio
  git remote add origin https://github.com/GuilleAV/gym-platform.git
  git push -u origin main
  ```

## Decisiones abiertas (bloquean la Fase 0, ver revisión §3)

> **ADR-0002 ACEPTADO (23/11/2026)** → `docs/adr/0002-sesiones-secretos-y-conexiones-por-tenant.md`.
> Cubre las decisiones 1 a 4. Cambio respecto de la revisión: los secretos generados por el alta
> automática van **cifrados en `gym_platform`** (AES-256-GCM, clave maestra fuera de la base), porque
> con solo variables de entorno el alta automática no puede funcionar sin reiniciar la app.
> **ADR-0003 redactado (Propuesto)** → `docs/adr/0003-superadministrador-y-sesion-de-soporte.md` (decisión 5,
> sesión de soporte con aprobación del gimnasio, modo emergencia, recuperación del único admin).
> Falta: que el usuario apruebe ADR-0003. Con eso arranca el paso 0.3.

| # | Decisión | Propuesta | ADR |
|---|---|---|---|
| 1 | Dónde viven las sesiones | Spring Session JDBC en la base de cada tenant | 0002 |
| 2 | Gestor de secretos | Variables de entorno / Docker secrets ahora; Vault en SaaS productivo | 0002 |
| 3 | Usuario de BD de aprovisionamiento | Credencial separada, solo para el job de alta | 0002 |
| 4 | Pools por tenant | HikariCP de 3 a 5 conexiones, mínimo 0 y desalojo de inactivos; revisar a ~50 tenants | 0002 |
| 5 | Rol del superadmin | Plantillas desde la plataforma + "sesión de soporte" auditada dentro del tenant | 0003 |

## Próximo paso (empezar acá en la próxima sesión)

**Al arrancar:** leer este archivo y el `CLAUDE.md`, y decirle al usuario "arrancá docker" si hace falta la base.

1. **Verificar los tests de integración** (no se confirmó al cerrar el 0.1): con Docker abierto, en IntelliJ →
   Maven → Lifecycle → `verify`. Tienen que pasar `MysqlVersionIT` y `CorreoNotificadorAlertasIT`.
   Si fallan, es lo primero que se corrige.
2. **Cerrar el paso 0.2** (rama `feature/paso-0-2`):
   - Generar el wrapper en IntelliJ: Maven → *Execute Maven Goal* → `mvn -N wrapper:wrapper -Dmaven=3.9.14 -Dtype=only-script`
     (con `backend` como proyecto). Claude lo commitea y marca `mvnw` como ejecutable (`git update-index --chmod=+x`).
   - `verify` en IntelliJ: tienen que pasar `ModularidadTest`, `MysqlVersionIT` y `CorreoNotificadorAlertasIT`.
   - Subir a GitHub, abrir PR a `main`, ver la CI en verde. Activar *Dependabot alerts* y *branch protection* en `main`.
3. **Antes del 0.3:** redactar ADR-0002 (sesiones, secretos, usuario de aprovisionamiento, pools) y ADR-0003 (superadmin). Son las decisiones abiertas de la tabla de arriba.

Plan completo de la Fase 0:

| Paso | Contenido | Estado |
|---|---|---|
| 0.1 | docker-compose (MySQL, Mailpit), versiones fijadas, almacenamiento (ADR-0004), aviso de disco | ✅ cerrado |
| 0.2 | Maven Wrapper + Spring Modulith + CI en GitHub Actions + Dependabot | 🔨 en curso |
| 0.3 | Base `gym_platform` con Flyway (gimnasio, dominio, conexión) | |
| 0.4 | Resolución del tenant (dominio → base), con limpieza del contexto | |
| 0.5 | Aprovisionamiento idempotente de gym_001 y gym_002 | |
| 0.6 | Spring Security: Argon2id, sesión en cookie, CSRF | |
| 0.7 | Suite de aislamiento entre tenants (rompe el build si falla) | |

## Entorno

- Windows. Proyecto en `C:\Gimnasio`.
- MySQL del proyecto en el puerto **3307** (el 3306 es del trabajo del usuario: no tocarlo).
- Node del trabajo: 20.19 (por defecto). El proyecto usa Node 24 vía **fnm** (`.nvmrc`).
- Java: el JDK 25 lo maneja IntelliJ (`~/.jdks/temurin-25.0.5.1`); no cambiar `JAVA_HOME` global.
- Docker Desktop con poca RAM asignada: no sumar contenedores sin necesidad. Hay contenedores ajenos al proyecto: no tocarlos.
- DBeaver: conexión "gym-platform DEV (3307)" a `127.0.0.1:3307` (con `allowPublicKeyRetrieval=true`).
- GitHub: usuario `GuilleAV`.
- Identidad git del repo: `GuilleAV <GuilleAV@users.noreply.github.com>`.
