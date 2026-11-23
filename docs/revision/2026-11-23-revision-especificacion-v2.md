# Revisión técnica de la especificación v2.0

**Fecha:** 23/11/2026 · **Revisor:** Claude (rol tutor / arquitecto) · **Documentos revisados:**
Requerimientos Integrales v2, Casos de Uso Integrales v2, Arquitectura, Datos y Facturación v2.

> Esto es una revisión técnica, no un asesoramiento legal ni contable. Los puntos marcados con ⚖️ necesitan la validación de un abogado o un contador.

## Veredicto general

La especificación es **sólida y está por encima del promedio**. Tiene aislamiento por tenant, cuota ≠ pago ≠ factura, idempotencia, adaptadores para los servicios externos, denegación por defecto y pruebas de aceptación verificables. La base conceptual está bien.

Lo que falta no es "más funcionalidad", sino **decisiones técnicas concretas** que hoy están abiertas, más algunos **huecos funcionales típicos de un gimnasio argentino** que el piloto (100 alumnos, hoy con planillas en papel) va a necesitar desde el día 1.

Escala de severidad: 🔴 Crítica · 🟠 Alta · 🟡 Media · 🟢 Baja

---

## 1. Stack desactualizado (actualizar antes de escribir código)

| Ítem | Spec dice | Recomiendo | Por qué |
|---|---|---|---|
| 🔴 Base de datos | MySQL 8 | **MySQL 8.4 LTS** | MySQL 8.0 llegó a fin de vida en abril de 2026 (versión 8.0.46). Arrancar un producto nuevo sobre una versión sin parches de seguridad es una deuda grave. |
| 🟠 Java | Java 21 | **Java 25 LTS** | Es la LTS vigente. Java 21 sigue soportado, pero un producto nuevo debería arrancar en la LTS más reciente para tener más años de soporte. |
| 🟠 Spring Boot | (sin versión) | **Spring Boot 4.1.x** | La línea 3.5 quedó sin soporte OSS el 30/06/2026. La 4.1 es la estable actual. |
| 🟡 Angular | (sin versión) | **Angular 22** | Es la versión activa, con soporte LTS hasta junio de 2028. Angular pasó a un ciclo anual y la v23 se espera para junio de 2027. |

**Acción:** registrar las versiones en `docs/adr/0001-stack-y-versiones.md` y fijarlas en el build (Maven BOM y `package.json`).

---

## 2. Inconsistencias internas de la especificación

### 🔴 2.1 El MVP (fase 1) depende de módulos de la fase 2
La fase 1 incluye **kiosco, check-in y móvil**, pero **cuotas, deuda y pagos** están en la fase 2. Sin embargo, ACC-04 ("deudas y acceso") y UC-14 ("comprobar regla de deuda") necesitan saber si el alumno debe.
**Consecuencia:** el check-in de la fase 1 no puede aplicar su regla principal, o hay que hacerlo dos veces.
**Propuesta:** pasar a la fase 1 un **"comercial mínimo"**: planes, membresía, cuota mensual, **cobro manual (efectivo o transferencia) en recepción** y saldo. Mercado Pago queda en la fase 2. Además, eso es lo que reemplaza la planilla de papel del piloto, o sea, lo que más valor le da al dueño.

### 🟠 2.2 El rol del superadministrador es contradictorio
- Arquitectura §9: "Superadministrador **no tiene lectura habitual** de datos sensibles; acceso excepcional".
- UC-08, UC-09 y UC-24: el superadmin **opera de forma rutinaria** dentro de gym_002 (cambia permisos de un profesor puntual).
- Pendiente §9: "Quién podrá operar permisos una vez realizada la configuración inicial", sin resolver.

**Propuesta:** el superadmin configura **plantillas y módulos desde gym_platform**. Cualquier operación dentro de un tenant se hace mediante una **"sesión de soporte"**: con un motivo, por tiempo limitado, auditada y, si es posible, aprobada por el admin del gimnasio. Después del alta, los permisos individuales los gestiona el **administrador del gimnasio**, dentro de los módulos contratados. Así se cierra el pendiente.

### 🟡 2.3 No existe la identidad del superadministrador
gym_platform no tiene tablas de usuarios de plataforma. Faltan `usuario_plataforma`, `sesion_soporte` y MFA **obligatorio** (no "propuesto") para todo usuario de plataforma y para todo admin de gimnasio.

---

## 3. Decisiones técnicas que faltan (bloquean la fase 0)

| # | Sev. | Decisión faltante | Propuesta |
|---|---|---|---|
| 3.1 | 🔴 | **Dónde viven las sesiones.** Las cookies HttpOnly + CSRF implican sesión en el servidor, pero no se dice en qué base. | Spring Session JDBC **en la base de cada tenant** (la tabla `sesion` ya está prevista). Así una sesión de gym_001 jamás puede resolverse en gym_002. Más adelante se puede mover a Redis con prefijo por tenant. |
| 3.2 | 🔴 | **Gestor de secretos.** "secret_ref" no dice a dónde apunta. | Fase 0–1: variables de entorno o Docker secrets. SaaS productivo: un gestor real (Vault o el del proveedor cloud). Detrás de una interfaz `SecretResolver` para no acoplarse. |
| 3.3 | 🔴 | **Usuario de BD para el aprovisionamiento.** Crear bases requiere privilegios altos (`CREATE DATABASE`, `CREATE USER`). | Una credencial **separada**, usada solo por el job de alta y nunca por la app en runtime. La app usa un usuario por tenant con permisos DML sobre **su** base y nada más. |
| 3.4 | 🟠 | **Límite de conexiones.** Un pool por tenant × N tenants puede agotar `max_connections` de MySQL. | HikariCP por tenant con `minimumIdle=0`, `maximumPoolSize` de 3 a 5 y desalojo de pools inactivos. **Umbral documentado:** revisar al llegar a unos 50 tenants activos por instancia, o cuando las conexiones abiertas pasen del 70 % del máximo. |
| 3.5 | 🟠 | **Jobs en segundo plano multi-tenant** (cuotas mensuales, cierre automático de asistencias, outbox). | Un scheduler que itera los tenants activos, fija el contexto de cada uno y lo limpia en un `finally`. Con **ShedLock** para que dos instancias no ejecuten el mismo job. |
| 3.6 | 🟠 | **Tabla outbox** mencionada pero no modelada. | Agregar `outbox_evento` al esquema operativo (id, tipo, payload, estado, intentos, clave de idempotencia). |
| 3.7 | 🟡 | **Fronteras entre módulos** sin mecanismo de control. | **Spring Modulith**: verifica en los tests que un módulo no use las clases internas de otro. Si mañana hay que separar un microservicio, las fronteras ya están limpias. |
| 3.8 | 🟡 | **Tiempo real** (WebSocket) con más de una instancia. | Para una instancia alcanza un broker simple. Si se escala horizontalmente, hace falta un relay (Redis o RabbitMQ). Documentarlo con su umbral. |
| 3.9 | 🟡 | **Zona horaria.** | Guardar todo en UTC (`TIMESTAMP`/`Instant`) y configurar la zona por gimnasio (por defecto `America/Argentina/Buenos_Aires`). Es crítico para vencimientos de cuotas y cierres automáticos. |
| 3.10 | 🟡 | **Dinero.** | `DECIMAL(12,2)` en la BD y `BigDecimal` en Java, **nunca** `double`. Moneda explícita (ARS). Con la inflación, los montos crecen rápido: no usar columnas chicas. |
| 3.11 | 🟡 | **Almacenamiento de multimedia.** | API compatible con S3: **MinIO** en local y desarrollo, S3 o R2 en SaaS. Mismo adaptador. Límites de tamaño, validación del tipo real del archivo y URLs firmadas que vencen. |
| 3.12 | 🟢 | **Observabilidad** sin herramientas definidas. | Logs JSON con `tenantId` y `requestId` (MDC), métricas con Micrometer y Prometheus. Nunca loguear datos personales ni de salud. |

---

## 4. Huecos funcionales (lo que el gimnasio real va a pedir)

| Sev. | Falta | Por qué importa |
|---|---|---|
| 🔴 | **Caja de recepción**: cobros en efectivo y transferencia, apertura y cierre de caja, arqueo y responsable. | En un gimnasio que hoy usa papel, la mayoría de los pagos son en efectivo o por transferencia. Sin caja no hay control de dinero ni detección de faltantes. |
| 🔴 ⚖️ | **Menores de edad**: consentimiento del padre, madre o tutor, y datos del responsable. | Los gimnasios tienen alumnos adolescentes. El consentimiento de un menor es un tema legal, no solo un campo más. |
| 🟠 ⚖️ | **Apto físico / certificado médico** con vencimiento y regla de acceso configurable. | Muchas jurisdicciones lo exigen. Validar la normativa local con un abogado. Encaja en la ficha de salud (protegida). |
| 🟠 | **Congelamiento de membresía** (vacaciones, lesión). | Es un pedido típico. Afecta los vencimientos de cuotas y paquetes. |
| 🟠 | **Reportes**: ingresos por período, morosos, asistencia, altas y bajas, ocupación por franja horaria. | Es lo que el dueño va a mirar todos los días. Hoy no hay ningún requerimiento de reportes. |
| 🟠 ⚖️ | **Derechos del titular de los datos** (acceso, rectificación, supresión, Ley 25.326) y su flujo. | LEG-02 lo menciona parcialmente. Falta un caso de uso concreto: exportar los datos de un alumno, borrarlos o anonimizarlos respetando la retención legal. |
| 🟠 ⚖️ | **Inscripción de la base de datos** ante la autoridad de protección de datos (AAIP). | Si se tratan datos sensibles (salud), consultar con un abogado la obligación de registro, tanto para el proveedor como para cada gimnasio. |
| 🟡 | **Descuentos y promociones** (familiar, estudiante, pago anticipado). | UC-12 dice "aplicar descuentos", pero no hay un requerimiento que los defina. |
| 🟡 | **Anti-abuso de formularios públicos**: captcha (Turnstile o hCaptcha), honeypot y rate limit. | UC-02 menciona el "abuso de formularios", pero no la defensa. |
| 🟡 | **WhatsApp como canal de aviso** (adaptador futuro, API oficial de WhatsApp Business). | En Argentina es el canal real. Por mail, los avisos de vencimiento no se leen. |
| 🟢 | **Cobro del SaaS a los gimnasios** (la licencia del proveedor). | `licencia` existe, pero no cómo se le cobra al gimnasio. Puede esperar a la fase 3. |

---

## 5. Seguridad: riesgos específicos detectados

| Sev. | Riesgo | Corrección propuesta |
|---|---|---|
| 🔴 ⚖️ | **El kiosco muestra "tarjetas de presentes" sin login**: expone públicamente quién está en el gimnasio (nombre o foto). | Mostrar solo un alias elegido por el alumno, sin foto por defecto, con opción de no aparecer, y con consentimiento explícito. Es un dato personal. |
| 🔴 | **PIN de kiosco con fuerza bruta.** Un PIN de 4 dígitos son solo 10.000 combinaciones. | Rate limit **por usuario y por dispositivo**, bloqueo progresivo, PIN de 6 dígitos, y el kiosco **enrolado** como dispositivo (token de dispositivo revocable), no un navegador cualquiera. |
| 🟠 | **QR dinámico compartido** (foto enviada por WhatsApp). | Token rotativo de unos 30 s firmado por el servidor (tipo TOTP), un solo uso por alumno y por día, y que lo escanee el celular **autenticado** del alumno. |
| 🟠 | **"Auditoría inmutable lógica"** sin mecanismo concreto. | Tabla append-only. El usuario de BD de la app **no tiene** `UPDATE`/`DELETE` sobre ella. Opcional: encadenar hashes (cada fila guarda el hash de la anterior) para detectar manipulación. |
| 🟠 | **Datos de salud sin cifrado a nivel de campo.** | Cifrado de aplicación (AES-GCM) para las columnas de salud, con una clave por tenant fuera de la BD. Así, un backup filtrado no expone esos datos. |
| 🟡 | **Archivos subidos** (documentos de inscripción, videos). | Validar el tipo real (magic bytes, no la extensión), limitar el tamaño, escanear con antivirus (ClamAV) y servir siempre desde otro dominio, nunca desde el de la app. |

---

## 6. Requisitos no funcionales sin números

La spec dice "rendimiento" y "disponibilidad", pero sin cifras. Sin números no se puede dimensionar el hardware ni saber si está bien. Propuesta inicial para validar con el dueño:

- **Piloto:** 1 gimnasio, unos 100 alumnos, unos 30 check-ins en hora pico y 5 usuarios internos simultáneos.
- **Latencia:** p95 < 300 ms en operaciones de recepción y check-in < 1 s de punta a punta.
- **Disponibilidad objetivo:** 99,5 % mensual (unas 3,6 h de caída por mes) para el SaaS inicial.
- **RPO/RTO iniciales:** RPO ≤ 24 h (backup diario más binlog si se quiere bajar), RTO ≤ 4 h. Medirlos con restauraciones reales antes de prometerlos por contrato, como ya dice la spec.

**Hardware sugerido para el piloto (SaaS):** un VPS de 2 vCPU, 4 GB de RAM y 60 GB SSD alcanza holgado para app + MySQL + MinIO en Docker. Los backups van **fuera** de ese servidor (otro proveedor u otra región). **No** exponer MySQL a Internet. Escalar cuando el CPU sostenido pase del 60 % o la RAM del 75 %.

---

## 7. Proceso de desarrollo (falta en los documentos)

- **CI** (GitHub Actions): build, tests unitarios, de integración con Testcontainers MySQL 8.4, **suite de aislamiento entre tenants** (si falla, el build falla), análisis de dependencias (Dependabot u OWASP Dependency-Check) y análisis estático.
- **Ramas:** `main` protegida, ramas `feature/*` y PR obligatorio aunque trabajes solo (así queda el historial y CI corre antes de mergear).
- **Entornos:** local (Docker Compose), staging y producción.
- **Migraciones por tenant:** aplicarlas primero a un tenant "canario" y después al resto. Si una falla, ese tenant queda marcado y no bloquea a los demás.
- **ADRs** (Architecture Decision Records) en `docs/adr/`, para que cada decisión quede escrita con su porqué.

---

## 8. Resumen de prioridades

**Antes de la primera línea de código (fase 0):**
1. Actualizar el stack (MySQL 8.4, Java 25, Spring Boot 4.1, Angular 22). → ADR-0001
2. Decidir sesiones, secretos, usuario de aprovisionamiento y pools. → ADR-0002 y ADR-0003
3. Resolver el rol del superadmin (sesión de soporte). → actualizar UC-08, UC-09 y UC-24.

**Para el MVP del piloto (fase 1):**
4. Mover el "comercial mínimo + caja de recepción" a la fase 1.
5. Agregar menores de edad, apto físico, congelamiento y reportes básicos.
6. Endurecer kiosco, PIN y QR según la sección 5.

**Consultas externas ⚖️ (en paralelo):** abogado (menores, apto físico, Ley 25.326, registro AAIP, exposición en el kiosco) y contador (criterio de facturación, ya listado como pendiente).
