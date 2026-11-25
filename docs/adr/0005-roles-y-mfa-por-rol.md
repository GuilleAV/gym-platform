# ADR-0005: Roles como datos y MFA definido por rol

- **Estado:** Aceptado (2026-11-23)
- **Fecha:** 2026-11-23
- **Decisores:** GuilleAV (dueño del producto: decisión), con propuesta técnica de Claude

## Contexto

El ADR-0003 dejó el MFA como obligatorio para administradores, pero los usuarios todavía no tenían roles.
Al diseñarlo, el dueño del producto definió cómo se tiene que decidir quién usa MFA:

1. **Por rol**, con un campo sí/no. No existe "opcional": el usuario no elige.
2. **Lo decide el superadmin, gimnasio por gimnasio** (en Gym Norte el recepcionista puede usar MFA y el
   profesor no; en otro gimnasio, al revés).
3. **El administrador del gimnasio no puede cambiarlo.**
4. **El MFA del propio superadmin también es configurable**, con aviso.
5. Los roles **tienen que poder crecer** (mañana: "Nutricionista", "Encargado de turno").
6. Un usuario puede tener **más de un rol** (ej. un profesor que a la tarde atiende la recepción).

## Decisión

### 1. Los roles son datos, no código

Tabla `rol` en la base de **cada gimnasio** (`codigo`, `nombre`, `mfa`, `activo`) y tabla `usuario_rol` (N a N).

- `codigo` (ej. `RECEPCION`) no cambia nunca: es lo que usa el código. `nombre` es lo que se ve en pantalla.
- Agregar un rol es insertar una fila desde el panel del superadmin: **sin programar ni hacer un deploy**.
  Con un `enum` de Java, cada rol nuevo exigiría un cambio de código y todos los gimnasios tendrían los mismos.
- Roles iniciales (los crea la migración V4 en cada gimnasio). Los valores de `mfa` son el punto de partida
  seguro, y el superadmin los cambia por gimnasio:

  | Código | Nombre | `mfa` inicial |
  |---|---|---|
  | `ADMINISTRADOR` | Administrador del gimnasio | sí |
  | `RECEPCION` | Recepción / caja | sí |
  | `PROFESOR` | Profesor | no |
  | `ALUMNO` | Alumno | no |

- Más adelante cada rol llevará también sus **permisos** (tabla `rol_permiso`). Este ADR no los define.

### 2. Regla del ingreso

Si **alguno** de los roles activos del usuario tiene `mfa = true`, el ingreso pide el segundo factor.
Si ninguno lo tiene, alcanza con la contraseña.

- Si el rol pasa a `mfa = true` y el usuario todavía no configuró su app, en el próximo ingreso tiene que
  configurarla (escanear el QR) antes de entrar. Nadie queda afuera.
- Si el rol pasa a `mfa = false`, el usuario entra sin código. Su configuración se conserva por si se reactiva.

### 3. Segundo factor: TOTP

- **TOTP (RFC 6238):** la app del celular (Google Authenticator, Microsoft Authenticator, etc.) y el servidor
  calculan el mismo código de 6 dígitos a partir de un secreto compartido y de la hora. Sin SMS (duplicación de
  SIM) y sin costo por mensaje.
- Secreto de 160 bits, **cifrado** con la clave maestra (AES-256-GCM, ADR-0002) en `usuario_mfa`.
- Tolerancia de ±30 segundos (relojes desfasados). **Un código no se puede usar dos veces** (se guarda el
  último paso de tiempo usado).
- **10 códigos de recuperación** de un solo uso, entregados una única vez al configurar, guardados con Argon2id.
- Los códigos incorrectos cuentan para el **límite de intentos** del paso 0.6b (por cuenta y por IP).

### 4. Flujo: la sesión recién se autentica al pasar todos los factores

1. `POST /api/sesion/ingresar` con correo y clave. Si no hace falta MFA: 204 y sesión, como antes.
   Si hace falta: 200 con `{"paso": "CODIGO_MFA"}` o `{"paso": "CONFIGURAR_MFA"}`, y una sesión
   **sin autenticar** que solo recuerda "pasó la contraseña" durante **5 minutos**.
2. `POST /api/sesion/mfa/verificar` (código o código de recuperación) → 204 y sesión autenticada.
3. Primera vez: `POST /api/sesion/mfa/configurar` (devuelve el secreto y la URI `otpauth://` para el QR) y
   `POST /api/sesion/mfa/confirmar` con el primer código → sesión autenticada + códigos de recuperación.

**Por qué no el MFA de Spring Security 7:** `@EnableMultiFactorAuthentication` está pensado para logins con
redirecciones y sus mecanismos propios (token por correo, passkeys, contraseña), y no trae TOTP. Nuestro login
es una API JSON. Con el flujo propio, hasta completar todos los factores el usuario es **anónimo** para el
resto del sistema: el "denegar por defecto" de la configuración actual lo cubre sin reglas nuevas.

### 5. Quién lo configura

- Solo el **superadmin**, por gimnasio, desde el panel de plataforma (`ServicioRoles`). El administrador del
  gimnasio no tiene ninguna operación para cambiar `mfa`.
- **MFA del superadmin:** configuración de la plataforma (en `gym_platform`), **activado por defecto**. Cada
  cambio queda en la auditoría de plataforma y **envía un correo a todos los superadmins**: si alguien lo apaga
  con una cuenta robada, el dueño se entera al instante.
- El panel de plataforma y la identidad del superadmin todavía no existen: esa parte (y el correo de aviso) se
  programa con ellos. Hoy `ServicioRoles` no tiene ningún endpoint HTTP, así que nadie puede cambiar `mfa`
  desde afuera.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Roles fijos en un `enum` | Cada rol nuevo exige programar y desplegar; todos los gimnasios tendrían los mismos roles. |
| MFA opcional que elige cada usuario | El dueño del producto decidió que lo define el rol, sin término medio. |
| MFA obligatorio según los **permisos** sensibles del usuario | Se automatiza solo, pero el dueño prefiere decidirlo explícitamente por rol. Se puede sumar como alerta ("este rol tiene permisos de salud y no tiene MFA") cuando existan los permisos. |
| Un solo rol por usuario | Obligaría a inventar roles combinados ("Profesor-Recepcionista"). |
| MFA por SMS | Vulnerable a duplicación de SIM y tiene costo por mensaje. |
| `@EnableMultiFactorAuthentication` de Spring Security 7 | Ver punto 4. Se revisa si Spring agrega TOTP o soporte para APIs JSON. |

## Consecuencias

- **Positivas:**
  - Roles nuevos y cambios de MFA sin tocar código.
  - Cada gimnasio puede tener una política distinta.
  - TOTP no tiene costo de operación.
- **Negativas / riesgos:**
  - **La primera configuración se hace con la contraseña sola:** si alguien tiene la contraseña de un usuario
    que todavía no configuró su app, podría configurar la suya. Se cierra con las invitaciones por correo
    (paso 0.6b parte 3), que hacen configurar el MFA al crear la cuenta.
  - Si el superadmin apaga el MFA de un rol con datos sensibles, esos datos quedan protegidos solo por contraseña.
  - TOTP necesita el **reloj del servidor en hora** (NTP). Un desfase de más de 30 segundos hace fallar los códigos.

## Cuándo revisar esta decisión (umbral)

- **Existen los permisos por rol** → evaluar la alerta de "rol con permisos sensibles sin MFA".
- **Más de 2 llamadas por mes por "perdí el celular"** en un gimnasio → sumar passkeys (WebAuthn) como alternativa.
- **Spring Security incorpora TOTP** o MFA para APIs JSON → evaluar migrar al mecanismo estándar.
- **Primer incidente** con una cuenta sin MFA → revisar los valores por defecto de los roles.
