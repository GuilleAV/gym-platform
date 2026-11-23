import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // HttpClient copia la cookie CSRF al header en cada POST/PUT/DELETE a nuestro propio dominio.
    // Nombres iguales a los del backend (SeguridadConfig): cookie con prefijo __Host- para que
    // otro subdominio (otro gimnasio) no pueda pisarla.
    provideHttpClient(
      withXsrfConfiguration({ cookieName: '__Host-XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
    ),
  ],
};
