import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DatosFicha, EnvioFicha, FichaAlumno, ResultadoFicha } from '../ficha/ficha.modelos';
import { resultadoDeError } from '../ficha/respuestas';

/** Completar la ficha con la invitación del correo (UC-05). */
@Injectable({ providedIn: 'root' })
export class FichaPorInvitacionService {
  private readonly http = inject(HttpClient);

  /** Valida el token y abre la sesión de ficha. null si la invitación no sirve (inexistente, usada o vencida). */
  async abrir(token: string): Promise<FichaAlumno | null> {
    try {
      return await firstValueFrom(this.http.post<FichaAlumno>('/api/invitacion/abrir', { token }));
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        return null;
      }
      throw error;
    }
  }

  async guardar(datos: DatosFicha): Promise<ResultadoFicha> {
    try {
      await firstValueFrom(this.http.put<void>('/api/ficha', datos));
      return { tipo: 'ok' };
    } catch (error) {
      return resultadoDeError(error);
    }
  }

  async enviar(envio: EnvioFicha): Promise<ResultadoFicha> {
    try {
      const r = await firstValueFrom(
        this.http.post<{ estado: 'ACTIVO' | 'EN_REVISION' }>('/api/ficha/enviar', envio),
      );
      return { tipo: 'ok', estado: r.estado };
    } catch (error) {
      return resultadoDeError(error);
    }
  }
}
