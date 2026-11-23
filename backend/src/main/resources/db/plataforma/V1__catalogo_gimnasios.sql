-- =============================================================================
-- V1 — Catálogo de la plataforma (base gym_platform).
-- Especificación: Arquitectura §5.1. Decisiones: ADR-0002.
--
-- REGLA: una migración aplicada NUNCA se edita. Para cambiar algo, crear V2, V3...
--
-- Convenciones:
--   - Fechas en UTC: el servidor corre con --default-time-zone=+00:00, así que
--     CURRENT_TIMESTAMP ya es UTC. DATETIME(6) = microsegundos y sin el límite
--     del año 2038 que tiene TIMESTAMP.
--   - Las restricciones CHECK son la última defensa: aunque el código tenga un
--     bug, la base rechaza datos inválidos (ej. un dominio en mayúsculas).
-- =============================================================================

-- Un gimnasio cliente. id = "ID global" de la especificación.
CREATE TABLE gimnasio (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    slug           VARCHAR(63)     NOT NULL,
    nombre         VARCHAR(150)    NOT NULL,
    estado         VARCHAR(20)     NOT NULL DEFAULT 'ALTA_PENDIENTE',
    modalidad      VARCHAR(10)     NOT NULL DEFAULT 'SAAS',
    -- Zona horaria del gimnasio: se usa para mostrar fechas y calcular vencimientos.
    zona_horaria   VARCHAR(64)     NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    creado_en      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_gimnasio_slug (slug),
    CONSTRAINT ck_gimnasio_slug      CHECK (slug REGEXP '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_gimnasio_estado    CHECK (estado IN ('ALTA_PENDIENTE', 'ACTIVO', 'SUSPENDIDO', 'BAJA')),
    CONSTRAINT ck_gimnasio_modalidad CHECK (modalidad IN ('SAAS', 'LOCAL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Dominios por los que se llega a cada gimnasio. Es la tabla que usa la resolución
-- del tenant (paso 0.4): host del request -> gimnasio. Por eso es crítica.
CREATE TABLE gimnasio_dominio (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    gimnasio_id  BIGINT UNSIGNED NOT NULL,
    -- ascii_bin: comparación EXACTA (sensible a mayúsculas). Con la collation por
    -- defecto, "Gym-Norte.com" y "gym-norte.com" serían iguales y el CHECK de abajo
    -- no podría detectar mayúsculas. Los dominios con acentos se guardan en punycode.
    host         VARCHAR(253) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tipo         VARCHAR(20)     NOT NULL DEFAULT 'PRINCIPAL',
    activo       BOOLEAN         NOT NULL DEFAULT TRUE,
    validado_en  DATETIME(6)     NULL,
    creado_en    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- Un dominio pertenece a UN solo gimnasio: nunca puede haber ambigüedad.
    UNIQUE KEY uk_dominio_host (host),
    KEY ix_dominio_gimnasio (gimnasio_id),
    CONSTRAINT fk_dominio_gimnasio FOREIGN KEY (gimnasio_id) REFERENCES gimnasio (id),
    -- Host normalizado: minúsculas, sin puerto, sin punto final, sin espacios.
    CONSTRAINT ck_dominio_host CHECK (host REGEXP '^[a-z0-9-]+(\\.[a-z0-9-]+)*$'),
    CONSTRAINT ck_dominio_tipo CHECK (tipo IN ('PRINCIPAL', 'ALIAS'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Dónde está la base de cada gimnasio y con qué usuario conectarse.
-- La CLAVE nunca está acá: solo una referencia a dónde buscarla (ADR-0002 §2).
CREATE TABLE gimnasio_conexion (
    gimnasio_id     BIGINT UNSIGNED   NOT NULL,
    host_bd         VARCHAR(253)      NOT NULL,
    puerto_bd       SMALLINT UNSIGNED NOT NULL,
    nombre_bd       VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    usuario_bd      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    secret_ref      VARCHAR(255)      NOT NULL,
    estado          VARCHAR(20)       NOT NULL DEFAULT 'PENDIENTE',
    version_esquema VARCHAR(50)       NULL,
    actualizado_en  DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (gimnasio_id),
    UNIQUE KEY uk_conexion_bd (host_bd, nombre_bd),
    CONSTRAINT fk_conexion_gimnasio FOREIGN KEY (gimnasio_id) REFERENCES gimnasio (id),
    -- Nombres con formato fijo: el alta automática arma SQL con ellos (CREATE DATABASE
    -- no admite parámetros), así que la base misma impide nombres "creativos".
    CONSTRAINT ck_conexion_nombre_bd  CHECK (nombre_bd REGEXP '^gym_[0-9]{3,}$'),
    CONSTRAINT ck_conexion_usuario_bd CHECK (usuario_bd REGEXP '^gym_[0-9]{3,}_app$'),
    CONSTRAINT ck_conexion_secret_ref CHECK (secret_ref REGEXP '^(env|file|cifrado|vault):[^ ]+$'),
    CONSTRAINT ck_conexion_estado     CHECK (estado IN ('PENDIENTE', 'LISTA', 'ERROR'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Secretos generados por el alta automática (claves de las bases gym_NNN),
-- cifrados con AES-256-GCM. La clave maestra NO está en la base (ADR-0002 §2).
CREATE TABLE secreto (
    id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    -- Con qué versión de la clave maestra se cifró: permite rotarla sin cortar el servicio.
    version_clave SMALLINT UNSIGNED NOT NULL,
    iv            BINARY(12)        NOT NULL,  -- nonce de GCM: único por cifrado
    cifrado       VARBINARY(1024)   NOT NULL,  -- texto cifrado + tag de autenticación
    creado_en     DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    rotado_en     DATETIME(6)       NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
