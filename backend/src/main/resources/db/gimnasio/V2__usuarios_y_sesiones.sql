-- =============================================================================
-- V2 — Usuarios y sesiones de cada gimnasio (paso 0.6, ADR-0002 §1).
-- =============================================================================

-- Usuarios del gimnasio (alumnos, profesores, administradores). Viven en la base de SU
-- gimnasio: el mismo correo en dos gimnasios son dos cuentas independientes.
CREATE TABLE usuario (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email          VARCHAR(254)    NOT NULL,   -- se guarda en minúsculas (lo normaliza la app)
    nombre         VARCHAR(150)    NOT NULL,
    -- Hash Argon2id. NULL mientras la cuenta está PENDIENTE (todavía no eligió contraseña).
    hash_clave     VARCHAR(255)    NULL,
    estado         VARCHAR(20)     NOT NULL DEFAULT 'PENDIENTE',
    creado_en      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuario_email (email),
    CONSTRAINT ck_usuario_estado CHECK (estado IN ('PENDIENTE', 'ACTIVO', 'SUSPENDIDO')),
    -- Última defensa: la base rechaza cualquier "hash" que no sea Argon2id (ej. una clave en texto plano).
    CONSTRAINT ck_usuario_hash CHECK (hash_clave IS NULL OR hash_clave LIKE '$argon2id$%'),
    CONSTRAINT ck_usuario_activo_con_clave CHECK (estado <> 'ACTIVO' OR hash_clave IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Sesiones HTTP (Spring Session JDBC). Estructura exigida por Spring Session (schema-mysql.sql),
-- con el nombre de tabla configurado en spring.session.jdbc.table-name = sesion.
-- Spring Session arma el nombre de la tabla de atributos como <tabla>_ATTRIBUTES: por eso
-- "sesion_ATTRIBUTES" (en Linux, MySQL distingue mayúsculas en nombres de tabla).
CREATE TABLE sesion (
    PRIMARY_ID            CHAR(36)     NOT NULL,
    SESSION_ID            CHAR(36)     NOT NULL,
    CREATION_TIME         BIGINT       NOT NULL,
    LAST_ACCESS_TIME      BIGINT       NOT NULL,
    MAX_INACTIVE_INTERVAL INT          NOT NULL,
    EXPIRY_TIME           BIGINT       NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100) NULL,
    CONSTRAINT pk_sesion PRIMARY KEY (PRIMARY_ID)
) ENGINE = InnoDB ROW_FORMAT = DYNAMIC;

CREATE UNIQUE INDEX ix_sesion_session_id ON sesion (SESSION_ID);
CREATE INDEX ix_sesion_expiry ON sesion (EXPIRY_TIME);
CREATE INDEX ix_sesion_principal ON sesion (PRINCIPAL_NAME);

CREATE TABLE sesion_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BLOB         NOT NULL,
    CONSTRAINT pk_sesion_attributes PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT fk_sesion_attributes FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES sesion (PRIMARY_ID) ON DELETE CASCADE
) ENGINE = InnoDB ROW_FORMAT = DYNAMIC;
