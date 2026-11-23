#!/bin/bash
# =============================================================================
# Crea la base del catálogo (gym_platform) y los usuarios MySQL de la plataforma.
# Diseño: ADR-0002 §3 (un usuario por función, mínimo privilegio).
#
# Cuándo corre:
#   - SOLO: MySQL lo ejecuta la PRIMERA vez que arranca con el volumen vacío
#     (todo lo que está en /docker-entrypoint-initdb.d se corre una única vez).
#   - A MANO: si la base ya existía antes de este script (ver infra/README.md):
#       docker compose -f infra/docker-compose.yml --env-file .env exec mysql \
#         bash /docker-entrypoint-initdb.d/01-plataforma.sh
#
# Es IDEMPOTENTE: correrlo dos veces no rompe nada y vuelve a sincronizar las
# claves con las que haya en .env.
#
# Ojo: el entrypoint de MySQL hace "source" de este archivo, así que no usamos
# "exit" ni "set -u" (cortarían al propio entrypoint).
# =============================================================================
set -eo pipefail

: "${MYSQL_ROOT_PASSWORD:?falta MYSQL_ROOT_PASSWORD}"
: "${GP_APP_DB_PASSWORD:?falta GP_APP_DB_PASSWORD en .env (ver .env.example)}"
: "${GP_APROVISIONADOR_DB_PASSWORD:?falta GP_APROVISIONADOR_DB_PASSWORD en .env (ver .env.example)}"

# Escapa comillas simples para meter la clave dentro de un literal SQL ('...').
sql_literal() { printf "%s" "${1//\'/\'\'}"; }
CLAVE_APP="$(sql_literal "$GP_APP_DB_PASSWORD")"
CLAVE_APROV="$(sql_literal "$GP_APROVISIONADOR_DB_PASSWORD")"

echo "[01-plataforma] creando gym_platform y usuarios (gp_app, gp_aprovisionador)"

# La clave de root va por MYSQL_PWD y no como "-p<clave>": así no aparece en la
# lista de procesos. Las claves de los usuarios nunca se imprimen.
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
-- Catálogo de la plataforma. NO guarda datos de alumnos ni de salud.
CREATE DATABASE IF NOT EXISTS gym_platform
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- gp_app: lo usa la aplicación en RUNTIME para leer el catálogo
-- (dominio -> gimnasio -> conexión). Solo datos (DML), nunca estructura (DDL):
-- aunque hubiera una inyección SQL, no puede crear, alterar ni borrar tablas.
-- '%' = desde cualquier host: la app corre fuera del contenedor y MySQL la ve
-- con la IP de la red de Docker. El puerto solo está publicado en 127.0.0.1.
-- En producción: restringir al host/red de la aplicación.
-- -----------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'gp_app'@'%' IDENTIFIED BY '${CLAVE_APP}';
ALTER USER 'gp_app'@'%' IDENTIFIED BY '${CLAVE_APP}';
GRANT SELECT, INSERT, UPDATE, DELETE ON gym_platform.* TO 'gp_app'@'%';

-- -----------------------------------------------------------------------------
-- gp_aprovisionador: SOLO para migraciones (Flyway) y el alta de gimnasios.
-- Crea las bases gym_NNN y sus usuarios. Nunca lo usa la parte web en producción.
-- El comodín \`gym\_%\` cubre gym_platform y todas las gym_NNN (el "\_" es un
-- guion bajo literal, no "cualquier carácter"). MySQL 8.4 marca los comodines en
-- GRANT como obsoletos: revisar al migrar a MySQL 9 (ADR-0002, umbrales).
-- -----------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'gp_aprovisionador'@'%' IDENTIFIED BY '${CLAVE_APROV}';
ALTER USER 'gp_aprovisionador'@'%' IDENTIFIED BY '${CLAVE_APROV}';
GRANT CREATE USER ON *.* TO 'gp_aprovisionador'@'%';
GRANT ALL PRIVILEGES ON \`gym\_%\`.* TO 'gp_aprovisionador'@'%' WITH GRANT OPTION;
SQL

echo "[01-plataforma] listo"
