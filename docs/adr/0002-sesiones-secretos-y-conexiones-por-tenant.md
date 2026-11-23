# ADR-0002: Sesiones, secretos y conexiones por tenant

- **Estado:** Propuesto
- **Fecha:** 2026-11-23
- **Decisores:** GuilleAV (dueño del producto), con propuesta técnica de Claude

## Contexto

La revisión de la especificación v2 (§3.1 a §3.4) dejó cuatro decisiones abiertas que bloquean los pasos
0.3 a 0.6. Las cuatro giran alrededor de lo mismo: **cómo se conecta la aplicación a N bases (una por
gimnasio) sin que un gimnasio pueda ver nada de otro**.

Restricciones:

- Tenant = dominio registrado + sesión validada. Nunca un `gimnasioId` enviado por el cliente.
- Un mismo producto para SaaS y para instalación local en un servidor del gimnasio (PLA-04).
- Alta de gimnasios **automática e idempotente** (PLA-03): crear base, usuario con clave propia, migraciones.
- "Nunca guardar credenciales de BD en texto plano" (Arquitectura §2).
- Hoy: un servidor, poca RAM, MySQL 8.4 con `max_connections` por defecto (151).

## Decisión

### 1. Sesiones: Spring Session JDBC en la base de cada gimnasio

- La sesión se guarda en la tabla `sesion` (más `sesion_atributos`) **de la base del tenant**, no en
  `gym_platform` ni en memoria. Así una sesión creada en gym_001 **no existe** en gym_002: no hay forma
  de "reusarla" en otro gimnasio aunque alguien robe la cookie.
- Cookie `__Host-SESION`: `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/` y **sin** atributo `Domain`.
  El prefijo `__Host-` obliga al navegador a cumplir esas tres cosas, y sin `Domain` la cookie de
  `gym-norte.ejemplo.com` nunca se envía a `power-fit.ejemplo.com`.
- Defensa en profundidad: la sesión guarda el `id_global` del gimnasio al iniciar sesión, y en cada
  request se compara con el tenant resuelto por dominio. Si no coinciden: se invalida y se deniega.
- Orden de filtros: el filtro que resuelve el tenant corre **antes** que el de Spring Session
  (`SessionRepositoryFilter`); si no, Spring Session leería la sesión sin saber en qué base buscarla.
- La limpieza automática de sesiones vencidas que trae Spring Session corre sin tenant: se **desactiva**
  y se reemplaza por un job propio que recorre los gimnasios activos (con limpieza del contexto en `finally`).
- Tiempos: inactividad 30 min (15 min para administradores), vencimiento absoluto 12 h. Al iniciar
  sesión se cambia el ID (contra *session fixation*). Al cambiar permisos de un usuario (UC-08) se
  borran sus sesiones activas.

### 2. Secretos: interfaz `SecretResolver` con referencias tipadas

`gimnasio_conexion.secret_ref` guarda una **referencia**, nunca la clave. El prefijo dice dónde está:

| Referencia | Dónde está el secreto | Uso |
|---|---|---|
| `env:NOMBRE` | Variable de entorno | Credenciales fijas: catálogo, aprovisionador, clave maestra |
| `file:/run/secrets/nombre` | Archivo (Docker secrets) | Igual que `env:`, en producción con Docker |
| `cifrado:<id>` | Tabla `secreto` de `gym_platform`, cifrada con AES-256-GCM | Claves que **genera el alta automática** (una por gimnasio) |
| `vault:<ruta>` | HashiCorp Vault u otro gestor | Futuro (ver umbral). Solo se agrega otra implementación |

- La **clave maestra** (`GYM_CLAVE_MAESTRA`, 256 bits) vive fuera de la base: variable de entorno o
  Docker secret. Un backup robado de `gym_platform` no alcanza para leer las claves de los gimnasios.
- Cada fila de `secreto` guarda el número de versión de la clave maestra que la cifró, para poder
  rotarla sin cortar el servicio (se descifra con la vieja y se re-cifra con la nueva).
- El valor descifrado nunca se loguea, nunca se devuelve por API y vive en memoria solo lo necesario
  para crear el pool.

**Por qué no solo variables de entorno:** el alta automática genera una clave nueva por gimnasio
mientras la aplicación está corriendo, y un proceso no puede agregarse variables de entorno a sí mismo.
Habría que reiniciar la app por cada gimnasio nuevo.

### 3. Usuarios de base de datos: uno por función, con el mínimo privilegio

| Usuario MySQL | Permisos | Quién lo usa |
|---|---|---|
| `gp_app` | `SELECT, INSERT, UPDATE, DELETE` sobre `gym_platform.*` | La app en runtime (catálogo: resolver dominio → gimnasio) |
| `gym_NNN_app` (uno por gimnasio) | `SELECT, INSERT, UPDATE, DELETE` sobre `gym_NNN.*` **y nada más** | La app en runtime, solo dentro del pool de ese gimnasio |
| `gp_aprovisionador` | `CREATE USER`; `ALL ... WITH GRANT OPTION` sobre `` `gym\_%`.* `` | Solo el job de alta y el de migraciones. Nunca la app web |
| `root` | Todo | Nadie en la aplicación. Solo administración manual |

- Ningún usuario de runtime tiene permisos de DDL (`CREATE`, `ALTER`, `DROP`). Si un atacante logra una
  inyección SQL en gym_001, no puede leer gym_002 (otra base, otro usuario) ni borrar tablas.
- Las migraciones de Flyway de cada `gym_NNN` las ejecuta el aprovisionador, no el usuario de runtime.

### 4. Conexiones: un pool HikariCP chico por gimnasio, creado a demanda

- Pool del catálogo (`gp_app`): fijo, `maximumPoolSize=5`.
- Pool por gimnasio: se crea la **primera vez** que llega un request de ese gimnasio, con
  `maximumPoolSize=4`, `minimumIdle=0` (sin conexiones abiertas si no hay uso) e `idleTimeout=10 min`.
- Los pools sin uso durante 30 minutos se **cierran** (caché con expiración que llama a `close()`).
- `maxLifetime` menor que el `wait_timeout` de MySQL, para que MySQL no corte conexiones que Hikari cree vivas.
- Métricas de Hikari por gimnasio expuestas con Micrometer (conexiones activas, en espera, tiempo de espera).

Cuenta de conexiones en el peor caso: `5 + 4 × gimnasios activos a la vez`. Con 151 de máximo y un 70 %
de margen (~105), entran unos **25 gimnasios con actividad simultánea**. Los que no tienen actividad no consumen nada.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Sesiones en `gym_platform` | Una sola tabla para todos los gimnasios: un error en el filtro por tenant expone sesiones ajenas. Contradice el aislamiento por base. |
| Sesiones en memoria (por defecto) | Se pierden en cada reinicio o deploy y no permiten más de una instancia. |
| Redis para sesiones desde el día 1 | Otro servicio que mantener, respaldar y asegurar, con poca RAM disponible. Queda como evolución (ver umbral). |
| Tokens JWT en `localStorage` | Robables por XSS y no se pueden revocar al instante (UC-08 pide invalidar sesiones al cambiar permisos). La spec pide cookies HttpOnly. |
| Secretos solo en variables de entorno | Rompe el alta automática (ver punto 2). |
| Vault desde el día 1 | Correcto para SaaS grande, pero desproporcionado para un servidor local de gimnasio. La interfaz `SecretResolver` deja la puerta abierta. |
| Un único usuario MySQL para todas las bases | Una inyección SQL en un gimnasio daría acceso a todos. Pierde la mitad del valor de tener bases separadas. |
| Un único pool con cambio de base (`USE gym_NNN`) | Obliga a un usuario con acceso a todas las bases y una conexión devuelta al pool sin el `USE` correcto mezcla datos entre gimnasios. |

## Consecuencias

- **Positivas:**
  - El aislamiento entre gimnasios se apoya en **MySQL** (bases y usuarios distintos), no solo en que el
    código no se equivoque.
  - Escala a N gimnasios sin reiniciar la app ni tocar configuración a mano.
  - Mismo diseño en SaaS y local; para pasar a Vault o Redis se cambia una implementación, no el diseño.
- **Negativas / deuda que asumimos:**
  - Hay que escribir código propio: enrutamiento de `DataSource`, caché de pools, job de limpieza de
    sesiones por tenant, cifrado de secretos y rotación de la clave maestra. Todo con tests (paso 0.4 a 0.7).
  - Si se pierde la clave maestra, se pierden las claves de las bases: hay que respaldarla **aparte**
    de la base (procedimiento de backup a documentar antes de producción).
  - Con más de una instancia de la app, cada una tiene sus propios pools: el cálculo de conexiones se
    multiplica por la cantidad de instancias.

## Cuándo revisar esta decisión (umbral)

- **Conexiones:** al superar el **70 % de `max_connections`** en las métricas, o **~25 gimnasios con
  actividad simultánea** por instancia → subir `max_connections`, poner ProxySQL delante o repartir
  gimnasios en más de una instancia de MySQL (la tabla `gimnasio_conexion` ya permite apuntar a otro host).
- **Sesiones:** al pasar a **más de una instancia de la app** detrás de un balanceador → evaluar Redis
  con prefijo por tenant (las sesiones JDBC siguen funcionando, pero agregan carga a cada base).
- **Secretos:** al operar como **SaaS con más de un servidor** o al exigirlo una auditoría → Vault o el
  gestor del proveedor cloud, como nueva implementación de `SecretResolver`.
