-- =============================================================================
-- V6 — Inscripción de alumnos (ADR-0007).
-- =============================================================================

-- La contraseña pasa a ser opcional (ADR-0006): una cuenta ACTIVA puede no tener clave (entra con código
-- o passkey). Y hay estados nuevos para la inscripción: INVITADO (completando la ficha) y EN_REVISION.
ALTER TABLE usuario DROP CHECK ck_usuario_activo_con_clave, DROP CHECK ck_usuario_estado;
ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_estado CHECK (estado IN ('PENDIENTE', 'INVITADO', 'EN_REVISION', 'ACTIVO', 'SUSPENDIDO'));

-- Configuración del gimnasio que define el superadmin (clave → valor). Ej.: inscripcion.modo = SIMPLE.
-- Clave/valor y no una columna por opción: agregar una opción nueva no requiere migrar la tabla.
CREATE TABLE configuracion_gimnasio (
    clave          VARCHAR(60)  NOT NULL,
    valor          VARCHAR(500) NOT NULL,
    actualizado_en DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (clave)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO configuracion_gimnasio (clave, valor) VALUES ('inscripcion.modo', 'SIMPLE');

-- Ficha del alumno (ALU-01). SEPARADA de la cuenta: un alumno puede no tener usuario (no usa correo) y un
-- usuario puede no ser alumno (ej. una recepcionista).
CREATE TABLE alumno (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nombre              VARCHAR(80)     NOT NULL,
    apellido            VARCHAR(80)     NOT NULL,
    email               VARCHAR(254)    NULL,      -- opcional; en minúsculas
    celular             VARCHAR(20)     NOT NULL,
    fecha_nacimiento    DATE            NOT NULL,
    -- Se completan en la ficha (paso siguiente):
    dni                 VARCHAR(15)     NULL,
    direccion           VARCHAR(200)    NULL,
    emergencia_nombre   VARCHAR(120)    NULL,
    emergencia_telefono VARCHAR(20)     NULL,
    objetivo            VARCHAR(30)     NULL,
    usuario_id          BIGINT UNSIGNED NULL,      -- la cuenta para la app, si tiene correo
    estado              VARCHAR(20)     NOT NULL DEFAULT 'FICHA_INCOMPLETA',
    creado_en           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_alumno_email (email),            -- MySQL permite varios NULL en un UNIQUE
    UNIQUE KEY uk_alumno_usuario (usuario_id),
    INDEX ix_alumno_apellido (apellido, nombre),
    CONSTRAINT fk_alumno_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE SET NULL,
    CONSTRAINT ck_alumno_estado CHECK (estado IN ('FICHA_INCOMPLETA', 'EN_REVISION', 'ACTIVO', 'INACTIVO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- El PROCESO de inscripción (quién la cargó, en qué etapa está, quién aprobó o rechazó y por qué).
CREATE TABLE inscripcion (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tipo           VARCHAR(10)     NOT NULL,           -- ALUMNO | PROFESOR
    origen         VARCHAR(10)     NOT NULL,           -- MOSTRADOR | WEB
    estado         VARCHAR(20)     NOT NULL,
    alumno_id      BIGINT UNSIGNED NULL,
    cargada_por    BIGINT UNSIGNED NULL,               -- usuario del personal (NULL si vino de la web)
    resuelta_por   BIGINT UNSIGNED NULL,               -- quien aprobó o rechazó al final
    motivo_rechazo VARCHAR(500)    NULL,               -- interno: no se le manda al interesado
    creada_en      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizada_en DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX ix_inscripcion_estado (estado, creada_en),
    CONSTRAINT fk_inscripcion_alumno FOREIGN KEY (alumno_id) REFERENCES alumno (id) ON DELETE CASCADE,
    CONSTRAINT fk_inscripcion_cargada FOREIGN KEY (cargada_por) REFERENCES usuario (id) ON DELETE SET NULL,
    CONSTRAINT fk_inscripcion_resuelta FOREIGN KEY (resuelta_por) REFERENCES usuario (id) ON DELETE SET NULL,
    CONSTRAINT ck_inscripcion_tipo CHECK (tipo IN ('ALUMNO', 'PROFESOR')),
    CONSTRAINT ck_inscripcion_origen CHECK (origen IN ('MOSTRADOR', 'WEB')),
    CONSTRAINT ck_inscripcion_estado CHECK (estado IN ('PENDIENTE', 'FICHA_PENDIENTE', 'EN_REVISION', 'APROBADA', 'RECHAZADA'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Invitaciones por correo para completar la ficha. Solo se guarda el SHA-256 del token.
CREATE TABLE invitacion (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    inscripcion_id BIGINT UNSIGNED NOT NULL,
    hash_token     CHAR(64)        NOT NULL,
    creada_ms      BIGINT          NOT NULL,
    expira_ms      BIGINT          NOT NULL,
    usada_ms       BIGINT          NULL,        -- usada, o anulada al reenviar
    PRIMARY KEY (id),
    UNIQUE KEY uk_invitacion_hash (hash_token),
    INDEX ix_invitacion_inscripcion (inscripcion_id, creada_ms),
    CONSTRAINT fk_invitacion_inscripcion FOREIGN KEY (inscripcion_id) REFERENCES inscripcion (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Auditoría: qué hizo quién y cuándo (SEG-07). Solo se agregan filas: nunca se modifican ni borran.
-- (Pendiente, ADR-0007 §7: quitarle al usuario de la app UPDATE y DELETE sobre esta tabla.)
CREATE TABLE auditoria (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ocurrio_en DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actor      VARCHAR(60)     NOT NULL,         -- 'usuario:12', 'sistema', 'web'
    accion     VARCHAR(60)     NOT NULL,         -- 'INSCRIPCION_CARGADA', 'INVITACION_ENVIADA'...
    recurso    VARCHAR(40)     NOT NULL,         -- 'inscripcion', 'alumno'...
    recurso_id BIGINT UNSIGNED NULL,
    detalle    VARCHAR(500)    NULL,             -- nunca datos de salud ni secretos
    PRIMARY KEY (id),
    INDEX ix_auditoria_recurso (recurso, recurso_id),
    INDEX ix_auditoria_fecha (ocurrio_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
