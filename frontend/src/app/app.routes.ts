import { Routes } from '@angular/router';
import { PaginaInicio } from './inicio/pagina-inicio';
import { soloPersonal } from './recepcion/solo-personal.guard';

/**
 * Rutas de la app. Inicio se carga enseguida (es lo primero que ve todo el mundo); invitación y recepción
 * se cargan en diferido (loadComponent): un alumno que solo entra a ver su rutina nunca descarga el
 * código de la recepción.
 */
export const routes: Routes = [
  { path: '', component: PaginaInicio },
  {
    path: 'invitacion',
    loadComponent: () => import('./invitacion/pagina-invitacion').then((m) => m.PaginaInvitacion),
  },
  {
    path: 'recepcion',
    canActivate: [soloPersonal],
    loadComponent: () => import('./recepcion/pagina-recepcion').then((m) => m.PaginaRecepcion),
  },
  { path: '**', redirectTo: '' },
];
