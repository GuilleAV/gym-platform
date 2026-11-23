# infra

Entorno de desarrollo local con Docker Compose.

| Servicio | Estado | Puerto (solo 127.0.0.1) |
|---|---|---|
| MySQL 8.4.13 LTS | ✅ listo | 3307 (configurable con `MYSQL_PORT` en `.env`) |
| Mailpit 1.34.0 (correo de prueba) | ✅ listo | SMTP 1025 · bandeja web http://localhost:8025 |

> **Fotos y videos:** no hay servicio extra. Se guardan en el disco del servidor
> (ver [ADR-0004](../docs/adr/0004-almacenamiento-multimedia.md)). MinIO quedó descartado (archivado en 2026).

## Uso (desde la raíz del repo, en PowerShell)

```powershell
Copy-Item .env.example .env      # y poner una clave real en .env
docker compose -f infra/docker-compose.yml --env-file .env up -d
docker compose -f infra/docker-compose.yml ps    # debe decir "healthy"
```

### Usuarios de la plataforma (paso 0.3, ADR-0002)

`infra/mysql/init/01-plataforma.sh` crea la base `gym_platform` y dos usuarios:
`gp_app` (la app: solo datos) y `gp_aprovisionador` (migraciones y alta de gimnasios).
MySQL lo corre solo la **primera vez** (volumen vacío). Si tu base ya existía de antes, corrélo
una vez a mano (es idempotente: se puede repetir sin problema):

```powershell
docker compose -f infra/docker-compose.yml --env-file .env up -d
docker compose -f infra/docker-compose.yml --env-file .env exec mysql bash /docker-entrypoint-initdb.d/01-plataforma.sh
```

Conexión desde DBeaver u otro cliente: host `127.0.0.1`, puerto `3307`.
**Ojo:** si te conectás a `localhost:3306`, vas a estar en tu MySQL del trabajo, no en el del proyecto.

La imagen de MySQL debe coincidir con `backend/pom.xml` (propiedad `mysql.image`):
el test `VersionesStackTest` falla si no coinciden.
