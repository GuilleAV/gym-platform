-- =============================================================================
-- V4 — Roles (como datos) y MFA por rol (paso 0.6b, ADR-0005).
-- =============================================================================

-- Roles del gimnasio. Son DATOS, no código: el superadmin puede crear roles nuevos para un gimnasio
-- ("Nutricionista") sin programar ni desplegar.
CREATE TABLE rol (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo         VARCHAR(40)     NOT NULL,   -- lo usa el código; no cambia nunca (ej. RECEPCION)
    nombre         VARCHAR(80)     NOT NULL,   -- lo que se ve en pantalla; editable
    mfa            BOOLEAN         NOT NULL DEFAULT FALSE,   -- ¿exige segundo factor al ingresar?
    activo         BOOLEAN         NOT NULL DEFAULT TRUE,
    creado_en      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rol_codigo (codigo),
    -- Mayúsculas, números y guion bajo: así el código es seguro para usar en logs, URLs y comparaciones.
    CONSTRAINT ck_rol_codigo CHECK (REGEXP_LIKE(codigo, '^[A-Z][A-Z0-9_]{1,39}$', 'c'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Punto de partida seguro (ADR-0005): quien maneja usuarios o dinero, con MFA.
-- El superadmin lo cambia por gimnasio.
INSERT INTO rol (codigo, nombre, mfa) VALUES
    ('ADMINISTRADOR', 'Administrador del gimnasio', TRUE),
    ('RECEPCION',     'Recepción / caja',           TRUE),
    ('PROFESOR',      'Profesor',                   FALSE),
    ('ALUMNO',        'Alumno',                     FALSE);

-- Un usuario puede tener varios roles (ej. profesor que también atiende la recepción).
CREATE TABLE usuario_rol (
    usuario_id BIGINT UNSIGNED NOT NULL,
    rol_id     BIGINT UNSIGNED NOT NULL,
    asignado_en DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (usuario_id, rol_id),
    INDEX ix_usuario_rol_rol (rol_id),
    -- Si se borra el usuario, se van sus asignaciones. Un rol en uso NO se puede borrar (se desactiva).
    CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_usuario_rol_rol FOREIGN KEY (rol_id) REFERENCES rol (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Configuración TOTP de cada usuario. El secreto va CIFRADO (AES-256-GCM con la clave maestra):
-- con un volcado de la base no se pueden generar códigos.
CREATE TABLE usuario_mfa (
    usuario_id      BIGINT UNSIGNED NOT NULL,
    secreto_cifrado VARBINARY(512)  NOT NULL,
    confirmado_en   DATETIME(6)     NULL,              -- NULL = escaneó el QR pero todavía no confirmó un código
    ultimo_paso     BIGINT          NOT NULL DEFAULT 0, -- último intervalo de 30 s usado: un código no sirve dos veces
    creado_en       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (usuario_id),
    CONSTRAINT fk_usuario_mfa_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Códigos de recuperación (si se pierde el celular). De un solo uso, guardados con Argon2id.
CREATE TABLE codigo_recuperacion (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    usuario_id  BIGINT UNSIGNED NOT NULL,
    hash_codigo VARCHAR(255)    NOT NULL,
    usado_en    DATETIME(6)     NULL,
    creado_en   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX ix_codigo_recuperacion_usuario (usuario_id),
    CONSTRAINT fk_codigo_recuperacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_codigo_recuperacion_hash CHECK (hash_codigo LIKE '$argon2id$%')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
