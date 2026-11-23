# MCP del proyecto: Engram y Context7

## ¿Qué es cada uno y para qué lo usamos?

| MCP | Qué hace | Uso en el proyecto |
|---|---|---|
| **Engram** | Memoria persistente para agentes de IA. Binario Go, guarda en SQLite local (`~/.engram/engram.db`), corre por *stdio* con `engram mcp`. | Recordar entre conversaciones: decisiones tomadas, bugs resueltos, convenciones, en qué paso de la fase estamos. |
| **Context7** | Documentación **actualizada** de librerías para LLMs. | Evitar código desactualizado: Spring Boot 4.1, Angular 22, Testcontainers, etc. cambian APIs entre versiones. |

## 1. Context7 (lo más simple)
En claude.ai → Settings → Connectors → agregar **Context7** (ya te apareció la tarjeta en el chat). Es remoto; no instala nada en tu PC.

## 2. Engram (servidor local)
Parece que ya lo tenés instalado (existe `%USERPROFILE%\.engram`). Verificalo en una terminal:

```powershell
engram version
```

Si no existe, seguir `docs/INSTALLATION.md` del repo de Engram.

### Registrarlo en Claude Desktop
Editar `%APPDATA%\Claude\claude_desktop_config.json` (en Claude Desktop: Settings → Developer → Edit Config) y agregar:

```json
{
  "mcpServers": {
    "engram": {
      "command": "engram",
      "args": ["mcp"]
    }
  }
}
```

> Si `engram` no está en el PATH, poner la ruta completa al `.exe` en `command` (con `\\` en JSON).

Reiniciar Claude Desktop. Las sesiones vinculadas a tu PC lo ven como servidor local.

### Registrarlo en Claude Code (si usás la terminal)
```bash
claude plugin marketplace add Gentleman-Programming/engram
claude plugin install engram
```

## Cómo vamos a trabajar con ellos
- **Al empezar cada sesión:** consultar Engram ("¿en qué quedamos en gym-platform?").
- **Antes de escribir código con una librería:** consultar Context7 por la versión exacta.
- **Al terminar:** guardar en Engram decisiones, problemas y próximo paso.
- Engram **no reemplaza** los ADR: lo importante se escribe también en `docs/adr/` porque el repo es la fuente de verdad compartible.
