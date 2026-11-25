# ADR-0006: Formas de ingreso: código por correo, passkeys y contraseña opcional

- **Estado:** Aceptado (2026-11-24)
- **Fecha:** 2026-11-24
- **Decisores:** GuilleAV (dueño del producto: decisión), con propuesta técnica de Claude

## Contexto

El dueño del producto pidió que entrar al sistema sea **lo más simple posible** para los usuarios. Hasta ahora
la única forma era la contraseña, más la app de códigos para los roles con MFA (ADR-0005): la combinación más
incómoda. La mayoría de los usuarios son alumnos que entran desde el celular.

## Decisión

### 1. Tres formas de ingreso, un solo sistema

| Forma | Qué hace el usuario | Factores | Etapa |
|---|---|---|---|
| **Código por correo** | Pone su correo y escribe el código de 6 dígitos que le llega | 1 (acceso a su correo) | Primera: es la base |
| **Passkey** | Toca "Ingresar" y usa la huella o la cara | 2 (el celular + la biometría) | Segunda |
| **Contraseña** | La escribe (opcional, escondida en "Prefiero usar contraseña") | 1 | Ya existe |

- La pantalla de ingreso pide primero **el correo** y después ofrece passkey o código. La contraseña no es lo
  primero que se ve: la mayoría de la gente nunca va a necesitar una.
- **Primero el código por correo, después la passkey:** el código funciona en cualquier celular o computadora sin
  configurar nada, y hace falta igual para las invitaciones y para recuperar el acceso. La passkey es la mejora:
  no puede ser la única puerta, porque un celular viejo dejaría a alguien afuera.
- **Un solo mecanismo para alumnos y personal.** Dos sistemas serían el doble de código, tests y errores. Lo que
  cambia entre roles es lo que se exige (punto 2).

### 2. MFA por rol (ADR-0005) con las nuevas formas

| Primer factor | Rol sin MFA | Rol con MFA |
|---|---|---|
| Passkey | Entra | Entra: la passkey ya es doble factor |
| Código por correo | Entra | Pide además la app de códigos (después: o una passkey) |
| Contraseña | Entra | Pide además la app de códigos (después: o una passkey) |

**El código por correo no alcanza para un rol con MFA:** si alguien le roba la cuenta de correo a una
recepcionista, entraría a la caja con solo eso. El segundo factor tiene que ser algo que se tiene (el celular).

### 3. Código por correo: reglas

- 6 dígitos, **válido 10 minutos**, de un solo uso, guardado como hash. Pedir uno nuevo anula el anterior.
- **Máximo 5 intentos por código**: después hay que pedir otro. Además cuentan para el límite de intentos del
  paso 0.6b (por cuenta y por IP).
- **Envíos limitados por usuario:** como mínimo 60 segundos entre uno y otro, y como máximo 5 por hora. Evita
  usar el sistema para llenarle la casilla a alguien y controla el costo.
- **No revela qué correos están registrados:** pedir un código responde siempre lo mismo (202), exista o no el
  correo. Solo se envía si el usuario existe y está activo.
- El correo se envía **en segundo plano**: así el tiempo de respuesta tampoco revela si el correo existe.

### 4. Contraseña (opcional): reglas simples (NIST SP 800-63B)

Mínimo 10 caracteres; **sin** reglas de composición ("una mayúscula, un número..."), porque solo generan claves
previsibles (`Gimnasio1!`); se rechazan contraseñas conocidas por filtraciones; nunca vence; se puede pegar y usar
el gestor de contraseñas. *(El rechazo de contraseñas filtradas se programa con las invitaciones.)*

### 5. Correos: módulo `notificaciones`

- Interfaz `Correos` (API pública del módulo). Implementación SMTP: Mailpit en desarrollo; en producción, un
  servicio transaccional (Brevo, Resend, Amazon SES) **por configuración**, sin tocar código.
- Envío asíncrono con hilos virtuales. Los logs nunca incluyen el destinatario ni el contenido (pueden llevar
  un código de ingreso).

## Alternativas consideradas

| Alternativa | Por qué se descartó (por ahora) |
|---|---|
| Solo passkeys | El primer usuario con un celular viejo queda afuera. |
| "Ingresar con Google" | Dependencia de un tercero; registro y mantenimiento de la app por cada dominio de cada gimnasio; no mejora la comodidad de la passkey. |
| Código por WhatsApp | Más cómodo en Argentina, pero la API oficial cobra por mensaje. Queda como opción paga por gimnasio. |
| Enlace mágico por correo (en vez de código) | Si se abre el correo en el celular y el ingreso era en la PC, el enlace abre la sesión en el lugar equivocado. El código se tipea donde se está ingresando. |
| Mantener la contraseña como forma principal | Es la más incómoda y la más insegura (reutilización, phishing). |

## Consecuencias

- **Positivas:**
  - La mayoría de los usuarios no necesita recordar ninguna contraseña.
  - El personal con passkey cumple el MFA con un toque.
  - Costo de operación casi nulo: los planes gratuitos de correo transaccional alcanzan para miles de envíos por mes.
- **Negativas / riesgos:**
  - **La seguridad del código por correo depende de la del correo del usuario** (por eso no alcanza para roles con MFA).
  - Si el servicio de correo falla, quien no tiene passkey ni contraseña no puede entrar. Se mitiga con un
    proveedor confiable y, a futuro, un segundo proveedor de respaldo.
  - El envío en segundo plano sin reintentos puede perder un correo si el SMTP falla: el usuario pide otro.
    Cuando haya correos que no se pueden perder (facturas), se pasa a una tabla outbox con reintentos.
  - Pedir códigos para muchos correos registrados sigue siendo posible (5 por hora por correo). Falta un captcha
    en los formularios públicos (revisión de la especificación, §4).

## Cuándo revisar esta decisión (umbral)

- **Más del 30 % de los usuarios no activa la passkey** después de 3 meses → evaluar "Ingresar con Google".
- **Un gimnasio pide WhatsApp** y acepta el costo → adaptador de WhatsApp Business.
- **Más de 1.000 códigos por correo por día** en una instancia, o el primer reclamo por correos que no llegan →
  outbox con reintentos y monitoreo de entregas.
- **Primer abuso detectado** del formulario de pedir código → captcha (Turnstile o hCaptcha) y límite por IP.
