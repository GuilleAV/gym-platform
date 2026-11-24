# ADR-0003: Superadministrador con acceso total y soporte acotado

- **Estado:** Aceptado (2026-11-23)
- **Fecha:** 2026-11-23
- **Decisores:** GuilleAV (dueño del producto: decisión), con propuesta técnica de Claude
- **Historia:** la primera versión (Propuesto) exigía que el gimnasio aprobara cada ingreso del
  superadmin. El dueño del producto la rechazó: **el superadmin tiene que poder acceder a todo, sin pedir permiso**.
  Este texto reemplaza esa propuesta (nunca estuvo aceptada, por eso se reescribe en lugar de crear un ADR nuevo).

## Contexto

La revisión de la especificación v2 encontró dos problemas (§2.2 y §2.3):

1. **Contradicción:** la Arquitectura §9 dice que el superadministrador "no tiene lectura habitual de datos
   sensibles", pero UC-08, UC-09 y UC-24 lo ponen a operar dentro de gym_002.
2. **No existe su identidad:** `gym_platform` no tiene tablas de usuarios de plataforma y el MFA figura como "propuesto".

Quedaba abierto, además, cómo recupera el acceso un gimnasio cuyo único administrador perdió clave y MFA.

Restricciones que se mantienen: tenant = dominio + sesión validada (nunca un `gimnasioId` enviado por el cliente);
ADR-0002 (sesión en la base del tenant, usuarios MySQL de mínimo privilegio); ningún supuesto sobre gym_001/gym_002.

## Decisión

### 1. El superadmin accede a todo, sin aprobación

- Rol de plataforma `SUPERADMIN`: **acceso total y directo a todos los gimnasios**, lectura y escritura,
  **incluidos los datos de salud**. No necesita motivo, ni aprobación del gimnasio, ni tiene límite de tiempo
  distinto del de cualquier sesión.
- Así se resuelve la contradicción de la spec: se corrige la Arquitectura §9 (el superadmin **sí** tiene acceso
  habitual) y UC-08, UC-09 y UC-24 pueden ejecutarlos tanto el superadmin como el administrador del gimnasio.

| | Superadmin (plataforma) | Administrador del gimnasio (tenant) |
|---|---|---|
| Dónde vive su usuario | `gym_platform.usuario_plataforma` | `gym_NNN.usuario` |
| Dónde se loguea | Dominio de la plataforma (ej. `admin.<dominio-plataforma>`) | Dominio del gimnasio |
| Qué hace | Todo: altas, dominios, licencias, módulos, plantillas **y operación dentro de cualquier gimnasio** | Usuarios, grupos, permisos y operación **de su** gimnasio |
| Acceso a datos del gimnasio | **Total, sin aprobación** | El que le den sus permisos |

### 2. Cómo entra a un gimnasio (sin romper el aislamiento)

Acceso total **no** significa saltarse el mecanismo de tenants: eso es lo que evita que un bug mezcle datos
de dos gimnasios.

1. En el panel de plataforma, el superadmin toca **"Entrar a Gym X"**.
2. El servidor genera un **token de un solo uso, válido 60 segundos** (en `gym_platform` se guarda solo su hash)
   y redirige al **dominio de ese gimnasio**, donde se canjea.
3. El canje crea una sesión **en la base del gimnasio** (ADR-0002) con actor `superadmin:<id>` y todos los permisos.
   No se crea ningún usuario en `gym_NNN.usuario`.
4. El gimnasio sigue saliendo del dominio y de la sesión. No existe un endpoint que reciba "qué gimnasio" por parámetro.

### 3. Protecciones que se mantienen (no limitan el acceso)

Estas medidas **no le piden permiso a nadie**. Existen porque una cuenta con acceso total es el objetivo más
valioso para un atacante: si se la roban, caen todos los gimnasios juntos.

- **MFA obligatorio** para el superadmin y para todo administrador de gimnasio: TOTP (app de autenticación)
  desde la Fase 0; passkeys (WebAuthn) como mejora. 10 códigos de recuperación de un solo uso, guardados como hash.
  **Nunca por SMS** (duplicación de SIM).
- **Sesión de plataforma corta:** Spring Session JDBC en `gym_platform`, cookie `__Host-` propia del dominio de
  plataforma; cierre por inactividad a los 15 minutos y vencimiento absoluto a las 8 horas.
- **Registro de auditoría** de lo que hace el superadmin, en `auditoria_plataforma` y en la auditoría del gimnasio.
  Es un **registro**, no un control: no bloquea nada. Sirve para saber qué pasó si hay un error, un reclamo o una
  cuenta robada. La aplicación solo puede hacer `INSERT` y `SELECT` sobre las tablas de auditoría (sin `UPDATE`
  ni `DELETE`), así ni un bug ni una inyección SQL pueden borrar el rastro.
- **Aviso al propio superadmin** (correo) cuando se loguea desde un dispositivo nuevo.
- El primer superadmin se crea con un comando de consola al instalar (`crear-superadmin`), nunca desde la web.
- **Visibilidad para el gimnasio:** por defecto, el gimnasio **no** ve los ingresos del superadmin. Queda como
  opción configurable para el futuro (ver umbrales).

### 4. Futuros empleados de soporte: acceso acotado

El acceso total es del **dueño del producto**. Si en el futuro se suma personal de soporte, tendrá el rol
`SOPORTE`, que **no** hereda el acceso total:

- Pide entrar a un gimnasio con motivo y alcance (`LECTURA`, `ESCRITURA`, aparte `SALUD`).
- Lo aprueba el superadmin o el administrador del gimnasio. Máximo 60 minutos, todo auditado.
- Mismo canje por token en el dominio del gimnasio (punto 2).

Este flujo se programa **recién cuando haya personal de soporte**, no en la Fase 0.

### 5. Recuperación del acceso cuando el único administrador lo perdió todo

1. El titular del gimnasio pide ayuda por un canal fuera del sistema.
2. Se verifica su identidad contra los datos del contrato en `gym_platform` (titular, CUIT) y **se le devuelve el
   llamado al teléfono registrado**, no al que llamó.
3. El superadmin entra al gimnasio, **resetea el MFA** del administrador y le envía **un enlace de restablecimiento
   a su correo registrado**.
4. Nadie ve, elige ni dicta una contraseña: siempre el mismo enlace temporal del flujo "olvidé mi contraseña".
5. El panel recomienda tener **al menos dos administradores** por gimnasio.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Sesión de soporte con aprobación del gimnasio también para el superadmin (versión anterior de este ADR) | El dueño del producto necesita operar sin depender de que el gimnasio responda. Se mantiene solo para el futuro rol `SOPORTE`. |
| Elegir el gimnasio desde el panel enviando su ID en cada request | Rompe la regla "nunca un `gimnasioId` del cliente": un error de autorización permitiría mezclar datos. El token canjeado en el dominio da el mismo acceso sin ese riesgo. |
| Crear un usuario "superadmin" dentro de cada `gym_NNN` | Habría que mantener N copias de la misma cuenta (claves, MFA, bajas). Una sola identidad en la plataforma es más simple y segura. |
| Acceso total sin MFA ni auditoría | Una sola contraseña robada expondría a todos los gimnasios sin dejar rastro. El MFA y la auditoría no le quitan ningún acceso al superadmin. |

## Consecuencias

- **Positivas:**
  - El dueño del producto opera cualquier gimnasio al instante, sin esperar aprobaciones.
  - Se cierra el pendiente §9 de la spec y la contradicción con UC-08, UC-09 y UC-24.
  - El aislamiento entre gimnasios sigue intacto en el código (dominio + sesión).
- **Negativas / riesgo que asumimos:**
  - **La cuenta de superadmin es un punto único de falla:** si se compromete, se exponen todos los gimnasios,
    incluidos datos de salud. Por eso el MFA no es opcional.
  - ⚖️ **Tema legal (validar con un abogado):** los datos de salud son datos sensibles (Ley 25.326). El contrato con
    cada gimnasio y la política de privacidad deben decir que el proveedor de la plataforma puede acceder a los
    datos para operar y dar soporte (rol de *encargado del tratamiento*). Sin eso, el acceso total puede ser un
    problema legal y comercial.
  - Algunos clientes (cadenas, gimnasios con médicos) pueden pedir lo contrario: que el proveedor no vea datos sin permiso.

## Cuándo revisar esta decisión (umbral)

- **Se suma la primera persona de soporte** → programar el rol `SOPORTE` con aprobación (punto 4).
- **Un cliente pide por contrato** que el proveedor no acceda sin permiso, o que se le avise → activar la
  visibilidad o la aprobación **por gimnasio**, como opción configurable.
- **Primer incidente de seguridad** que involucre una cuenta de plataforma → revisar todo este ADR.
- **Primer cliente que pida certificación** (ISO 27001 o similar) → revisión periódica de accesos y passkeys obligatorias.
- El abogado indica que hace falta otro mecanismo para los datos de salud ⚖️.
