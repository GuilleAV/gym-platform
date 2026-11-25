-- =============================================================================
-- V5 — Ingreso con código por correo (ADR-0006 §3).
-- =============================================================================

-- Códigos de 6 dígitos que se mandan por correo. Válidos 10 minutos, de un solo uso, hasta 5 intentos.
-- Se guarda el SHA-256 (no el código): quien vea la tabla no puede usar un código vigente con solo leerlo.
-- Tiempos en milisegundos desde 1970 (UTC), como "sesion" e "intento_ingreso".
CREATE TABLE codigo_ingreso (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    usuario_id    BIGINT UNSIGNED NOT NULL,
    hash_codigo   CHAR(64)        NOT NULL,
    creado_ms     BIGINT          NOT NULL,
    expira_ms     BIGINT          NOT NULL,
    intentos      INT UNSIGNED    NOT NULL DEFAULT 0,
    usado_ms      BIGINT          NULL,       -- usado para ingresar, o anulado al pedir uno nuevo
    PRIMARY KEY (id),
    -- Para "¿cuántos códigos le mandé en la última hora?" y "¿cuál es el vigente?".
    INDEX ix_codigo_ingreso_usuario_creado (usuario_id, creado_ms),
    CONSTRAINT fk_codigo_ingreso_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
