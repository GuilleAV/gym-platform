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


## Probar la pantalla de un gimnasio (con el backend corriendo en el perfil demo)

```powershell
npm start
```

Abrí **http://gym-norte.localhost:4200** o **http://power-fit.localhost:4200** (Chrome o Edge).

- `proxy.conf.json` reenvía `/api` al backend (`localhost:8080`) **sin cambiar el Host**: el backend ve
  `gym-norte.localhost` y responde como Gym Norte. Así se prueba el multi-gimnasio sin tocar el archivo hosts.
- `allowedHosts: [".localhost"]` (angular.json) permite que el servidor de desarrollo acepte esos dominios.
- Usuario de demo: `admin@gym-norte.localhost` (o `admin@power-fit.localhost`), con la clave `GYM_DEMO_CLAVE`
  de tu `.env`.
