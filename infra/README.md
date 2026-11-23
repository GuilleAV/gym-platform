# infra

Entorno de desarrollo local con Docker Compose.

| Servicio | Estado | Puerto (solo 127.0.0.1) |
|---|---|---|
| MySQL 8.4.13 LTS | ✅ listo | 3307 (configurable con `MYSQL_PORT` en `.env`) |
| MinIO (S3 para multimedia) | ⏳ pendiente (paso 0.1) | — |
| Mailpit (correo de prueba) | ⏳ pendiente (paso 0.1) | — |

## Uso (desde la raíz del repo, en PowerShell)

```powershell
Copy-Item .env.example .env      # y poner una clave real en .env
docker compose -f infra/docker-compose.yml --env-file .env up -d
docker compose -f infra/docker-compose.yml ps    # debe decir "healthy"
```

Conexión desde DBeaver u otro cliente: host `127.0.0.1`, puerto `3307`.
**Ojo:** si te conectás a `localhost:3306`, vas a estar en tu MySQL del trabajo, no en el del proyecto.

La imagen de MySQL debe coincidir con `backend/pom.xml` (propiedad `mysql.image`):
el test `VersionesStackTest` falla si no coinciden.
