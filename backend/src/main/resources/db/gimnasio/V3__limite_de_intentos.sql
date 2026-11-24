-- =============================================================================
-- V3 — Límite de intentos de inicio de sesión (paso 0.6b).
-- =============================================================================

-- Cuenta los fallos de ingreso por CUENTA (correo) y por IP, y hasta cuándo están bloqueados.
-- Se cuenta exista o no el correo: así un 429 no revela qué correos están registrados.
--
-- Privacidad: no se guardan el correo ni la IP en claro, sino su SHA-256. Es una seudonimización
-- (no anonimización: una IPv4 se puede adivinar probando todas), pero evita que un volcado de esta
-- tabla sea una lista legible de correos e IPs.
--
-- Tiempos en milisegundos desde 1970 (UTC), igual que la tabla "sesion": sin problemas de zona horaria.
CREATE TABLE intento_ingreso (
    tipo               VARCHAR(10)     NOT NULL,   -- CUENTA | IP
    clave_hash         CHAR(64)        NOT NULL,   -- SHA-256 en hexadecimal del correo o la IP
    fallos             INT UNSIGNED    NOT NULL,
    ultimo_fallo_ms    BIGINT          NOT NULL,
    bloqueado_hasta_ms BIGINT          NOT NULL DEFAULT 0,   -- 0 = sin bloqueo
    PRIMARY KEY (tipo, clave_hash),
    INDEX ix_intento_ingreso_ultimo_fallo (ultimo_fallo_ms),
    CONSTRAINT ck_intento_ingreso_tipo CHECK (tipo IN ('CUENTA', 'IP'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
