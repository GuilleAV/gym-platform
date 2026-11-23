# ADR-0001: Stack tecnológico y versiones

- **Estado:** Propuesto (pendiente de aprobación del dueño del proyecto; ver `docs/ESTADO.md`)
- **Fecha:** 2026-11-23
- **Decisores:** Dueño del producto + Claude (rol arquitecto)
- **Origen:** Revisión técnica de la especificación v2, sección 1 ("Stack desactualizado").

## Contexto

La especificación v2 nombra el stack (monolito modular Spring Boot, Angular/PWA, MySQL, Flyway, Docker)
pero con versiones viejas o sin versión:

- **MySQL 8:** la línea 8.0 llegó a fin de vida en abril de 2026. Arrancar un producto nuevo
  sobre una base sin parches de seguridad es inaceptable: el sistema guarda datos de salud
  (datos sensibles, Ley 25.326) y dinero.
- **Java 21:** sigue soportado, pero ya no es la LTS más reciente.
- **Spring Boot y Angular:** sin versión definida. "La última" no es una versión:
  dos personas (o dos builds) en días distintos obtendrían cosas distintas.

Además, una versión que solo está escrita en un documento no protege nada. Tiene que estar
**fijada en el build** y el build tiene que **fallar** si alguien usa otra.

## Decisión

| Componente | Versión fijada | Dónde se fija | Soporte aproximado |
|---|---|---|---|
| Java (JDK) | **25 LTS** (Temurin recomendado) | `backend/pom.xml` → `java.version` + Maven Enforcer `[25,26)` | LTS; la próxima LTS es Java 29 (sept. 2027) |
| Spring Boot | **4.1.2** | `backend/pom.xml` → `spring-boot-starter-parent` | OSS hasta ~junio 2027 |
| Maven | **3.9.14** (mín. 3.9.9) | Maven Enforcer + Maven Wrapper (`backend/.mvn/wrapper/maven-wrapper.properties`) | — |
| MySQL | **8.4.13 LTS** | `infra/docker-compose.yml` y `backend/pom.xml` → `mysql.image` (un test verifica que coincidan) | Premier hasta ~abril 2029, extendido hasta ~2032 |
| Testcontainers | la que gestiona Spring Boot 4.1.2 (línea 2.0.x) | BOM de Spring Boot (no se repite la versión) | — |
| Flyway | la que gestiona Spring Boot 4.1.2 | BOM de Spring Boot (se agrega en el paso 0.3) | — |
| Spring Modulith | **2.1.1** (línea 2.1 = Boot 4.1) | `backend/pom.xml` → `spring-modulith.version` + BOM | Al pasar a Boot 4.2 → Modulith 2.2 |
| Tests e2e | Playwright | se agrega con el primer flujo crítico del frontend | — |
| Angular | **22.3.0** (exacto, sin `^`) | `frontend/package.json` + `.npmrc save-exact` | Activa hasta jun. 2027, LTS hasta jun. 2028 |
| Node.js | **24 LTS** recomendado (mín. 22.22) | `.nvmrc` + `engines` en `package.json` + `engine-strict` | Node 24 LTS hasta abril 2028 |
| TypeScript | la que exige Angular 22 (6.x) | `frontend/package.json` | — |

### Reglas que acompañan esta decisión

1. **Versiones exactas.** Nada de rangos (`^`, `~`, `LATEST`, imágenes `:latest`). Un build de hoy
   y uno de dentro de un año con el mismo commit deben usar lo mismo.
2. **Una sola fuente por versión.** Las dependencias Java las gestiona el BOM de Spring Boot;
   solo se declara una versión a mano cuando Spring Boot no la gestiona, y se documenta por qué.
3. **El build lo verifica.** Maven Enforcer corta el build con un JDK o Maven incorrecto;
   `engine-strict` corta `npm install` con un Node incorrecto; un test compara la imagen de MySQL
   del `docker-compose.yml` con la de los tests de integración.
4. **Actualizaciones de parche:** mensuales, vía Dependabot (se configura en el paso de CI).
   Un parche de seguridad se aplica apenas sale.

## Alternativas consideradas

| Alternativa | Por qué se descartó (por ahora) |
|---|---|
| MySQL 9.7 LTS | Es una LTS más nueva y con más años de soporte. Pero hoy los servicios administrados (Amazon Aurora, Google Cloud SQL) recién están adoptando 8.4, y el SaaS probablemente termine en uno de ellos. Pasar de 8.4 LTS a 9.7 LTS es un camino de actualización soportado. Ver umbral. |
| Mantener Java 21 | Funciona, pero perderíamos 2 años de soporte y Spring Boot 4.1 ya soporta hasta Java 25. Java 25 es, a la vez, la LTS más nueva y la versión más alta que soporta Spring Boot 4.1. |
| Spring Boot 4.2 | Está en release candidate. No se arranca un producto sobre una versión no GA. Ver umbral. |
| Gradle en vez de Maven | Ambos sirven. Maven es más declarativo y predecible para alguien que recién empieza con Spring, y la spec no lo condiciona. |
| PostgreSQL | Técnicamente muy buena opción, pero la especificación validada fija MySQL y no hay un motivo fuerte para cambiarla. |
| H2 para tests | **Prohibido** por las reglas del proyecto: H2 no se comporta como MySQL (tipos, collation, SQL). Los tests de integración usan MySQL 8.4 real con Testcontainers. |

## Consecuencias

**Positivas**
- Años de parches de seguridad por delante en todas las piezas.
- Builds reproducibles: el mismo commit produce el mismo artefacto.
- Si alguien usa otra versión por error, se entera en el primer build, no en producción.

**Negativas / deuda asumida**
- Hay que instalar JDK 25 y Node 24 en cada máquina de desarrollo (o usar SDKMAN / nvm).
- Spring Boot 4.1 tiene soporte OSS hasta ~junio 2027: hay que planificar el salto a 4.2 o superior.
- Versiones exactas implican mantenimiento activo (Dependabot + revisión de PRs).
- Muchas guías de internet están escritas para Spring Boot 3 o Angular ≤ 20: antes de usar una API, consultar documentación actualizada (MCP Context7).

## Cuándo revisar esta decisión (umbral)

- **Spring Boot 4.2:** migrar cuando salga la **4.2.1** (el primer parche, con los bugs iniciales
  corregidos) y antes de **marzo 2027**.
- **MySQL 9.7 LTS:** reevaluar cuando el proveedor cloud elegido para el SaaS ofrezca 9.7 administrado,
  o antes de **2028**.
- **Angular 23:** sale ~junio 2027; migrar antes de que termine el soporte activo de la 22.
- **Java 29 LTS:** evaluar cuando salga (sept. 2027) y Spring Boot la soporte oficialmente.
- Cualquier CVE crítico sin parche en una versión fijada obliga a reabrir este ADR de inmediato.
