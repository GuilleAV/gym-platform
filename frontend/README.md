# Frontend (Angular 22)

Versiones fijadas en [ADR-0001](../docs/adr/0001-stack-y-versiones.md).

- `.nvmrc`: versión de Node recomendada (`nvm use`).
- `.npmrc`: `save-exact=true` (todo `npm install x` guarda la versión exacta, sin `^`)
  y `engine-strict=true` (npm se niega a instalar con un Node fuera de `engines`).
- `package-lock.json`: se versiona siempre. En CI se usa `npm ci`, que instala exactamente lo del lock.

| Comando | Para qué |
|---|---|
| `npm start` | Servidor de desarrollo en http://localhost:4200 |
| `npm test` | Tests en modo watch (mientras programás) |
| `npm run test:ci` | Tests en una sola pasada (CI) |
| `npm run build` | Build de producción en `dist/` |
