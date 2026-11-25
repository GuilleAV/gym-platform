-- =============================================================================
-- V7 — Ficha del alumno y documentos legales versionados (ADR-0007 §4 y §8, UC-05, UC-25).
-- =============================================================================

-- Datos del responsable (padre, madre o tutor) para alumnos menores de edad. ⚖️ Validar con un abogado.
ALTER TABLE alumno
    ADD COLUMN responsable_nombre   VARCHAR(120) NULL,
    ADD COLUMN responsable_dni      VARCHAR(15)  NULL,
    ADD COLUMN responsable_telefono VARCHAR(20)  NULL,
    ADD COLUMN responsable_email    VARCHAR(254) NULL,
    ADD COLUMN responsable_vinculo  VARCHAR(10)  NULL,
    ADD CONSTRAINT ck_alumno_responsable_vinculo CHECK (responsable_vinculo IS NULL OR responsable_vinculo IN ('MADRE', 'PADRE', 'TUTOR')),
    ADD CONSTRAINT ck_alumno_objetivo CHECK (objetivo IS NULL OR objetivo IN
        ('BAJAR_DE_PESO', 'FUERZA', 'MUSCULACION', 'SALUD', 'RENDIMIENTO', 'OTRO'));

-- Documentos legales del gimnasio, VERSIONADOS: un texto aceptado nunca se modifica; si cambia, se crea
-- una versión nueva y se marca la anterior como no vigente. Así siempre se sabe qué texto exacto aceptó cada uno.
CREATE TABLE documento_legal (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo        VARCHAR(40)     NOT NULL,           -- DESLINDE, PRIVACIDAD, CONSENTIMIENTO_MENOR
    version       INT UNSIGNED    NOT NULL,
    titulo        VARCHAR(150)    NOT NULL,
    texto         TEXT            NOT NULL,
    solo_menores  BOOLEAN         NOT NULL DEFAULT FALSE,
    vigente       BOOLEAN         NOT NULL DEFAULT TRUE,
    creado_en     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_documento_codigo_version (codigo, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- TEXTOS DE EJEMPLO: el abogado los reemplaza creando una versión 2 (sin tocar código). ⚖️
INSERT INTO documento_legal (codigo, version, titulo, texto, solo_menores) VALUES
('DESLINDE', 1, 'Deslinde de responsabilidad',
 '[TEXTO DE EJEMPLO: reemplazar por el validado por un abogado] Declaro que realizo actividad física bajo mi propia responsabilidad, que informaré al gimnasio cualquier condición de salud relevante y que seguiré las indicaciones del personal.',
 FALSE),
('PRIVACIDAD', 1, 'Política de privacidad y tratamiento de datos',
 '[TEXTO DE EJEMPLO: reemplazar por el validado por un abogado] El gimnasio trata mis datos personales para gestionar mi inscripción, membresía y entrenamiento, conforme a la Ley 25.326. Puedo pedir acceso, rectificación o supresión de mis datos.',
 FALSE),
('CONSENTIMIENTO_MENOR', 1, 'Consentimiento del padre, madre o tutor',
 '[TEXTO DE EJEMPLO: reemplazar por el validado por un abogado] Como responsable del menor, autorizo su inscripción y la realización de actividad física, y acepto los documentos anteriores en su nombre.',
 TRUE);

-- Quién aceptó qué versión, cuándo y por qué medio (UC-25).
CREATE TABLE aceptacion_documento (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    alumno_id      BIGINT UNSIGNED NOT NULL,
    documento_id   BIGINT UNSIGNED NOT NULL,
    aceptado_en    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    medio          VARCHAR(12)     NOT NULL,          -- INVITACION (lo aceptó la persona) | MOSTRADOR
    aceptado_por   VARCHAR(120)    NOT NULL,          -- nombre de quien aceptó: el alumno o su responsable
    registrado_por BIGINT UNSIGNED NULL,              -- usuario del personal, si fue en el mostrador
    ip             VARCHAR(45)     NULL,              -- desde dónde se aceptó por invitación (IPv6: hasta 45)
    PRIMARY KEY (id),
    UNIQUE KEY uk_aceptacion (alumno_id, documento_id),
    CONSTRAINT fk_aceptacion_alumno FOREIGN KEY (alumno_id) REFERENCES alumno (id) ON DELETE CASCADE,
    CONSTRAINT fk_aceptacion_documento FOREIGN KEY (documento_id) REFERENCES documento_legal (id) ON DELETE RESTRICT,
    CONSTRAINT fk_aceptacion_registrado FOREIGN KEY (registrado_por) REFERENCES usuario (id) ON DELETE SET NULL,
    CONSTRAINT ck_aceptacion_medio CHECK (medio IN ('INVITACION', 'MOSTRADOR'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
