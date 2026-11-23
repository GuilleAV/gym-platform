# Estado del proyecto gym-platform

> **Leer esto primero al empezar cada conversación nueva.** Se actualiza al cerrar cada sesión de trabajo.

**Última actualización:** 23/11/2026 · **Fase actual:** Fase 0 — paso 0.2 en curso (rama `feature/paso-0-2`)

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

- [~] **Paso 0.2 — EN CURSO** (rama `feature/paso-0-2`, sin mergear):
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
