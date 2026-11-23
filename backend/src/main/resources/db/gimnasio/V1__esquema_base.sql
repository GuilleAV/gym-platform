-- =============================================================================
-- V1 — Esquema COMÚN de cada gimnasio (gym_001, gym_002, ... gym_N).
-- Todas las bases de gimnasio corren exactamente estas migraciones.
-- REGLA: una migración aplicada NUNCA se edita. Para cambiar algo, crear V2, V3...
--
-- Por ahora solo la tabla de parámetros: usuarios, sesiones, alumnos, etc. llegan en
-- los pasos siguientes (0.6 en adelante), cada uno con su migración.
-- =============================================================================

-- Parámetros configurables por el gimnasio (ej. mensaje de bienvenida de la landing).
CREATE TABLE parametro_gimnasio (
    clave          VARCHAR(64)   NOT NULL,
    valor          VARCHAR(2000) NOT NULL,
    actualizado_en DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (clave),
    CONSTRAINT ck_parametro_clave CHECK (clave REGEXP '^[a-z0-9_]+$')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
