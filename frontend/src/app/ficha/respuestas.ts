import { HttpErrorResponse } from '@angular/common/http';
import { ResultadoFicha } from './ficha.modelos';

/** Convierte un error HTTP de guardar o enviar la ficha en un resultado que la pantalla sabe mostrar. */
export function resultadoDeError(error: unknown): ResultadoFicha {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 400) {
      return { tipo: 'errores', errores: error.error?.errores ?? ['Revisá los datos'] };
    }
    if (error.status === 409) {
      return {
        tipo: 'no-editable',
        mensaje: error.error?.error ?? 'La ficha ya no se puede modificar',
      };
    }
    if (error.status === 401 || error.status === 403 || error.status === 404) {
      return { tipo: 'sin-acceso' };
    }
  }
  throw error;
}
