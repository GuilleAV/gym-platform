# ADR-0003: Rol del superadministrador y sesión de soporte

- **Estado:** Propuesto
- **Fecha:** 2026-11-23
- **Decisores:** GuilleAV (dueño del producto), con propuesta técnica de Claude

## Contexto

La revisión de la especificación v2 encontró dos problemas (§2.2 y §2.3):

1. **Contradicción:** la Arquitectura §9 dice que el superadministrador "no tiene lectura habitual de datos
   sensibles", pero UC-08, UC-09 y UC-24 lo ponen a operar todos los días dentro de gym_002.
2. **No existe su identidad:** `gym_platform` no tiene tablas de usuarios de plataforma, y el MFA figura como "propuesto".

Además quedó abierto (ver ADR-0002 y la conversación del 23/11): **¿cómo recupera el acceso un gimnasio
cuyo único administrador perdió clave y MFA?** Alguien de la plataforma tiene que poder ayudar, sin que
eso se convierta en una puerta trasera permanente a los datos de los alumnos (incluidos datos de salud).

Restricciones: tenant = dominio + sesión validada (nunca un `gimnasioId` del cliente); ADR-0002 (sesión
en la base del tenant, usuarios MySQL de mínimo privilegio); ningún supuesto sobre gym_001/gym_002.

## Decisión

### 1. Dos niveles de administración, con fronteras claras

| | Superadmin / soporte (plataforma) | Administrador del gimnasio (tenant) |
|---|---|---|
| Dónde vive su usuario | `gym_platform.usuario_plataforma` | `gym_NNN.usuario` |
| Dónde entra | Dominio propio de la plataforma (ej. `admin.<dominio-plataforma>`) | Dominio del gimnasio |
| Qué hace habitualmente | Alta de gimnasios, dominios, licencias, **módulos contratados**, **plantillas** de grupos y permisos, monitoreo | Usuarios, grupos y permisos **dentro** de los módulos contratados, operación diaria |
| Acceso a datos del gimnasio | **Ninguno** por defecto. Solo con una sesión de soporte (punto 3) | El que le den sus permisos |

Así se resuelve la contradicción: UC-08/UC-09/UC-24 los ejecuta el **administrador del gimnasio**. El
superadmin define las plantillas iniciales al dar de alta el gimnasio y, después, solo entra con soporte.

### 2. Identidad de la plataforma

- Tablas nuevas en `gym_platform`: `usuario_plataforma`, `usuario_plataforma_mfa`,
  `codigo_recuperacion_plataforma`, `sesion_soporte` y `auditoria_plataforma`.
- Roles de plataforma: `SUPERADMIN` (todo) y `SOPORTE` (solo pide y usa sesiones de soporte; no toca licencias ni altas).
- **MFA obligatorio** para todo usuario de plataforma y todo administrador de gimnasio: TOTP (app de
  autenticación) desde la Fase 0; passkeys (WebAuthn) como mejora. Al activarlo se entregan 10 códigos de
  recuperación de un solo uso, guardados como hash.
- Las sesiones de plataforma usan Spring Session JDBC **en `gym_platform`** (igual que el ADR-0002 pero
  en el catálogo), cookie `__Host-` propia del dominio de plataforma. Inactividad 15 min, absoluto 8 h.
- El **primer** superadmin no se crea por la web: lo crea un comando de consola al instalar
  (`crear-superadmin`), que envía una invitación. Siempre debe haber **al menos dos** superadmins activos,
  para que uno pueda recuperar al otro.

### 3. Sesión de soporte: entrar a un gimnasio con motivo, permiso, tiempo y registro

Flujo normal:

1. Un usuario de plataforma **solicita** soporte para un gimnasio, con **motivo** obligatorio y
   **alcance**: `LECTURA` (por defecto), `ESCRITURA` y, aparte, `SALUD` (ver datos de salud).
2. Un **administrador del gimnasio la aprueba** desde su panel. El alcance `SALUD` necesita aprobación explícita.
3. Al aprobarse, el soporte recibe un **token de un solo uso válido 60 segundos** (en `gym_platform`
   se guarda solo su hash) y se lo canjea **en el dominio del gimnasio**. El gimnasio sigue saliendo del
   dominio y la sesión, como manda la regla: no hay un "elegir gimnasio" con un ID del cliente.
4. El canje crea una sesión **en la base del gimnasio** (ADR-0002) marcada como soporte: el actor es
   `soporte:<usuario_plataforma>`, con los permisos del alcance aprobado y **no** los de un usuario real.
   No se crea ningún usuario en `gym_NNN.usuario`.
5. Dura **60 minutos como máximo** (una sola extensión, con motivo). Se cierra sola al vencer, o antes si
   el soporte o el admin del gimnasio la terminan. El admin ve en su panel las sesiones de soporte activas.
6. **Todo queda registrado dos veces:** en `auditoria_plataforma` (quién pidió, quién aprobó, cuándo,
   motivo) y en la auditoría del gimnasio (cada operación hecha con esa sesión).

**Modo emergencia ("romper el vidrio")**, solo cuando no hay ningún admin del gimnasio que pueda aprobar:

- Lo aprueba un **segundo** superadmin distinto del que lo pide (control de cuatro ojos).
- Se avisa por correo **al titular del gimnasio** (contacto registrado en el catálogo) en el momento.
- Alcance máximo `ESCRITURA`; `SALUD` nunca por esta vía.
- Queda marcado para revisión posterior en la auditoría.

### 4. Recuperación del acceso cuando el único administrador lo perdió todo

1. El titular del gimnasio pide ayuda por un canal fuera del sistema (teléfono o correo de contacto).
2. Soporte **verifica la identidad** contra los datos del contrato que están en `gym_platform`
   (titular, CUIT) y **devuelve el llamado al teléfono registrado**, no al que llamó.
3. Sesión de soporte en modo emergencia (punto 3), con un único objetivo: **resetear el MFA** del
   administrador y **enviarle un enlace de restablecimiento a su correo registrado**.
4. El soporte **nunca** ve, elige ni dicta una contraseña. Siempre se usa el mismo enlace temporal que el
   flujo normal de "olvidé mi contraseña".
5. El panel del gimnasio recomienda tener **al menos dos administradores**, para no depender de soporte.

### 5. Refuerzo del ADR-0002: la auditoría no se puede borrar desde la aplicación

El usuario MySQL de runtime de cada gimnasio (`gym_NNN_app`) tendrá sobre las tablas de auditoría solo
`INSERT` y `SELECT`, sin `UPDATE` ni `DELETE`. Lo mismo `gp_app` sobre `auditoria_plataforma`. Ni un bug
ni una inyección SQL pueden borrar el rastro de una sesión de soporte. (Esto agrega un detalle al
ADR-0002 sin cambiar su decisión.)

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Superadmin con acceso directo y permanente a todos los gimnasios | Una sola cuenta comprometida expone a todos los gimnasios y sus datos de salud. Contradice la Arquitectura §9 y la Ley 25.326 (datos sensibles). |
| Crear un usuario "soporte" dentro de cada `gym_NNN` | Queda una cuenta con permisos dentro de cada gimnasio todo el tiempo: es una puerta trasera, aunque esté deshabilitada. |
| Elegir el gimnasio desde el panel de plataforma con su ID | Rompe la regla "nunca un `gimnasioId` del cliente": un error de autorización permitiría saltar a cualquier gimnasio. El token canjeado en el dominio mantiene la regla. |
| Soporte sin aprobación del gimnasio | Más rápido, pero el gimnasio no se entera de quién mira sus datos. Se permite solo como emergencia, con cuatro ojos y aviso al titular. |
| Que soporte ponga una contraseña temporal y se la dicte al admin | La contraseña pasa por una persona y un canal no seguro (teléfono, WhatsApp). Siempre enlace al correo registrado. |
| MFA por SMS | Vulnerable a duplicación de SIM (*SIM swapping*), muy común. TOTP y passkeys no dependen de la línea. |

## Consecuencias

- **Positivas:**
  - Ningún usuario de plataforma puede leer datos de un gimnasio sin dejar rastro y, en el caso normal,
    sin permiso del propio gimnasio. Es un argumento de venta: "tus datos son tuyos".
  - La recuperación del único admin tiene un procedimiento claro, sin puertas traseras.
  - Se cierra el pendiente §9 de la spec y se corrigen UC-08, UC-09 y UC-24 (actor: administrador del gimnasio).
- **Negativas / deuda que asumimos:**
  - Más pantallas y lógica: panel de aprobación en el gimnasio, canje de token, modo emergencia, alertas.
  - El soporte es más lento que un acceso directo (hace falta aprobación). Es el precio del aislamiento.
  - Con un solo superadmin (hoy, el dueño del proyecto) el modo emergencia de cuatro ojos no se puede
    cumplir: hasta tener un segundo superadmin se reemplaza por **aviso al titular + revisión posterior**,
    y queda anotado como deuda.

## Cuándo revisar esta decisión (umbral)

- **Más de 3 personas en soporte** o un cliente que lo exija por contrato → roles de plataforma más finos
  y acceso del personal con SSO de la empresa.
- **Más de 2 sesiones de soporte por gimnasio por mes** en promedio → el producto tiene un hueco de
  usabilidad: revisar qué pide soporte y convertirlo en funciones del administrador del gimnasio.
- **Primer cliente que pida certificación** (ISO 27001 o similar) → revisar retención de auditoría,
  revisión periódica de accesos y passkeys obligatorias.
