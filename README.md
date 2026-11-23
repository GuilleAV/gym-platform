# gym-platform

Sistema integral de gestión de gimnasios — **SaaS multi-gimnasio** con opción de despliegue local, mismo código.

## Stack (ver `docs/adr/0001-stack-y-versiones.md`)

| Capa | Tecnología |
|---|---|
| Backend | Java 25 LTS · Spring Boot 4.1 · Spring Security · Spring Modulith · Flyway |
| Frontend | Angular 22 · PWA |
| Base de datos | MySQL 8.4 LTS — `gym_platform` (catálogo) + una base por gimnasio (`gym_001`, `gym_002`, …) |
| Infra | Docker / Docker Compose · MinIO (S3) para multimedia |
| Tests | JUnit 5 · Testcontainers (MySQL real) · Angular testing · e2e |

## Estructura

```
gym-platform/
├── backend/            # Spring Boot (monolito modular)          [fase 0]
├── frontend/           # Angular PWA                              [fase 0]
├── infra/              # docker-compose, scripts de provisión    [fase 0]
├── docs/
│   ├── especificacion/ # Requerimientos, casos de uso, arquitectura (fuente de verdad)
│   ├── revision/       # Revisiones técnicas de la especificación
│   ├── adr/            # Architecture Decision Records
│   └── setup/          # Guías de entorno (MCP, herramientas)
└── CLAUDE.md           # Reglas del proyecto para el asistente de IA
```

## Fases

| Fase | Entrega |
|---|---|
| 0 — Fundaciones | gym_platform + 2 bases ficticias, resolución segura de tenant, Spring Security, pruebas de aislamiento |
| 1 — MVP piloto | Landing, inscripciones, ABM, rutinas, kiosco, móvil, **comercial mínimo + caja** (propuesto) |
| 2 — Operación | Mercado Pago, agenda, permisos avanzados, notificaciones, auditoría reforzada |
| 3 — Comercial | Facturación ARCA, suscripciones, molinetes |

## Reglas no negociables

- Nunca confiar en un `gimnasioId` enviado por el cliente: el tenant se resuelve por dominio + sesión.
- Ningún secreto en el repositorio.
- No se mergea código sin tests; la suite de aislamiento entre tenants rompe el build si falla.
- Cuota ≠ pago ≠ factura.
