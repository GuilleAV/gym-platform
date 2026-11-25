# ADR-0007: Inscripción de alumnos (mostrador primero, aprobación configurable)

- **Estado:** Aceptado (2026-11-24)
- **Fecha:** 2026-11-24
- **Decisores:** GuilleAV (dueño del producto: decisión), con propuesta técnica de Claude
- **Requisitos:** INS-01 a INS-06, UC-02 a UC-06, WEB-03, ALU-01; revisión de la especificación §4 (menores, apto físico)

## Contexto

La especificación define la inscripción con **doble aprobación**: preinscripción → aprobación inicial → invitación
por correo → completar ficha → revisión final → activación. El piloto (unos 100 alumnos, hoy con planillas en
papel) necesita sobre todo **cargar alumnos en el mostrador**. El ADR-0006 cambió el ingreso a código por correo
(sin contraseña), y parte de la gente de un gimnasio de barrio no usa correo.

## Decisión

### 1. Aprobación configurable por gimnasio

| Modo | Flujo | Para quién |
|---|---|---|
| `SIMPLE` (por defecto) | Preinscripción → aprobación → ficha → **activo automáticamente** | Gimnasios chicos, el piloto |
| `COMPLETO` (el de la especificación) | Preinscripción → aprobación → ficha → **revisión final** → activo | Gimnasios que quieren revisar cada ficha |

- Lo define el **superadmin** al configurar cada gimnasio (`configuracion_gimnasio`, clave `inscripcion.modo`).
- **La carga en el mostrador cuenta como la aprobación inicial:** quien inscribe en persona ya vio al interesado.

### 2. Orden de construcción

1. **Mostrador** (este paso): Administrador o Recepción cargan la preinscripción.
2. **Completar la ficha** con la invitación (o en el mostrador si no tiene correo).
3. **Pantallas** de Angular para los dos pasos anteriores.
4. **Formulario público** en la web (con captcha) y bandeja de aprobación para el modo `COMPLETO`.

### 3. Quién puede inscribir

Los roles `ADMINISTRADOR` y `RECEPCION`. Es la **primera autorización por rol** del sistema: los roles de cada
usuario (ADR-0005) pasan a ser *authorities* de Spring Security (`ROLE_<codigo>`). Cuando existan los permisos
por rol (`rol_permiso`), la regla pasa de "estos roles" a "quien tenga el permiso `inscripcion.cargar`".
Un cambio de roles se aplica en el próximo ingreso del usuario.

### 4. Datos

- **Preinscripción (mínimo):** nombre, apellido, correo (opcional), celular y **fecha de nacimiento**. La fecha
  detecta menores desde el principio.
- **Ficha (siguiente paso):** DNI, dirección, contacto de emergencia, objetivo; salud y apto físico solo si el
  gimnasio los tiene activados (UC-09).
- **Menores:** se aceptan; la ficha pide los datos del padre, madre o tutor, y **el responsable** acepta los
  documentos. ⚖️ Validar con un abogado.

### 5. Sin correo: inscripto, pero sin app

- El correo es **opcional**. Quien no tiene queda inscripto (y después la recepción completa su ficha en el
  mostrador), pero **no tiene cuenta** para la app. Entra al gimnasio con el **PIN del kiosco** (UC-15, más adelante).
- Por eso se separa **la ficha (`alumno`) de la cuenta (`usuario`)**: un alumno puede no tener usuario, y un
  usuario (ej. una recepcionista) puede no ser alumno. A futuro, el código por **WhatsApp** como opción paga.

### 6. Invitación

- Enlace con un token aleatorio de 256 bits, **guardado como SHA-256**, de un solo uso, **válido 7 días**.
  Reenviarla anula la anterior (como mínimo 1 minuto entre reenvíos).
- El token va en el **fragmento** de la URL (`/invitacion#t=...`): el navegador no lo manda al servidor, así que
  no queda en logs de acceso, proxies ni en el `Referer`.
- La cuenta se crea con estado `INVITADO` (sin contraseña: ADR-0006). Solo se activa al terminar la ficha.

### 7. Reglas de privacidad y auditoría

- Si el correo ya existe en el gimnasio, **en el mostrador** se avisa (el personal tiene que saberlo). En el
  formulario público **no** se avisa, para no revelar quién es alumno.
- Si se rechaza una solicitud, se avisa al interesado con un correo genérico. El motivo queda interno.
- **Auditoría** (tabla `auditoria`, módulo `auditoria`): quién cargó, invitó, reenvió, aprobó o rechazó, y cuándo.
  *(Pendiente: quitarle al usuario de la app el `UPDATE`/`DELETE` sobre esa tabla, desde el aprovisionamiento.)*
- **Inscribirse no es pagar:** la membresía y la cuota son del módulo comercial, independientes de la cuenta.

### 8. Documentos legales

Textos de ejemplo, **versionados**: queda registrado quién aceptó qué versión y cuándo (UC-25). El abogado los
reemplaza sin tocar código. ⚖️

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Solo doble aprobación (como la especificación) | Burocracia para un gimnasio chico: aprobar dos veces a alguien que ya se vio en el mostrador. |
| Correo obligatorio | Deja afuera a gente que no usa correo; el gimnasio no puede perder alumnos por eso. |
| Código por WhatsApp desde el día 1 | Tiene costo por mensaje. Queda como opción paga por gimnasio. |
| Formulario público primero | Necesita captcha y bandeja de aprobación; el piloto necesita antes el mostrador. |
| Token de invitación en la URL (`?t=`) o en la ruta | Queda en logs, historial y `Referer`. El fragmento (`#t=`) no sale del navegador. |
| Una sola tabla para alumno y usuario | No permite alumnos sin cuenta ni personal que no sea alumno. |

## Consecuencias

- **Positivas:**
  - El piloto reemplaza la planilla de papel desde el primer paso.
  - Nadie queda afuera por no tener correo.
  - El flujo de la especificación sigue disponible (modo `COMPLETO`).
- **Negativas / riesgos:**
  - Dos modos de aprobación duplican algunos caminos de prueba.
  - Un alumno sin correo depende de la recepción para cualquier cambio de datos.
  - Hasta que exista el permiso `inscripcion.cargar`, la regla está atada a nombres de roles.

## Cuándo revisar esta decisión (umbral)

- **Se crean los permisos por rol** → pasar de "roles ADMINISTRADOR/RECEPCION" a "permiso `inscripcion.cargar`".
- **Más del 20 % de los inscriptos sin correo** en un gimnasio → priorizar WhatsApp o el PIN del kiosco.
- **Primer abuso** del formulario público → captcha más estricto y límite por IP.
- El abogado pide cambios en menores, apto físico o consentimientos ⚖️.
