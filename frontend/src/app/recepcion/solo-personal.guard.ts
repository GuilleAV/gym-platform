import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SesionService } from '../sesion/sesion.service';

/**
 * Deja entrar a /recepcion solo al personal (Administrador o Recepción). Espera a saber quién es: si
 * alguien entra directo con el enlace, primero se carga la sesión.
 *
 * Es comodidad, no seguridad: si alguien se lo saltea, el backend le responde 403 a cada pedido.
 */
export const soloPersonal: CanActivateFn = async () => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  await sesion.esperarCarga();
  return sesion.esPersonal() ? true : router.parseUrl('/');
};
