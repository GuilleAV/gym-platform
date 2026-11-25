import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DatosFicha, EnvioFicha, FichaAlumno, ResultadoFicha } from '../ficha/ficha.modelos';
import { resultadoDeError } from '../ficha/respuestas';

export interface NuevaInscripcion {
  nombre: string;
  apellido: string;
  email: string | null;
  celular: string;
  fechaNacimiento: string;
}

export interface InscripcionCargada {
  id: number;
  alumnoId: number;
  estado: string;
  invitacionEnviada: boolean;
  esMenor: boolean;
}

export interface ResumenInscripcion {
  id: number;
  nombre: string;
  apellido: string;
  email: string | null;
  celular: string;
  estado: string;
  origen: string;
  creadaEn: string;
}

export type ResultadoInscribir =
  | { tipo: 'ok'; inscripcion: InscripcionCargada }
  | { tipo: 'errores'; errores: string[] }
  | { tipo: 'conflicto'; mensaje: string };

/** Lo que hace el personal en el mostrador (ADR-0007). Todos estos endpoints exigen rol en el backend. */
@Injectable({ providedIn: 'root' })
export class InscripcionesService {
  private readonly http = inject(HttpClient);

  async inscribir(datos: NuevaInscripcion): Promise<ResultadoInscribir> {
    try {
      const inscripcion = await firstValueFrom(
        this.http.post<InscripcionCargada>('/api/inscripciones', datos),
      );
      return { tipo: 'ok', inscripcion };
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 400) {
        return { tipo: 'errores', errores: error.error?.errores ?? ['Revisá los datos'] };
      }
      if (error instanceof HttpErrorResponse && error.status === 409) {
        return { tipo: 'conflicto', mensaje: error.error?.error ?? 'No se pudo inscribir' };
      }
      throw error;
    }
  }

  pendientes(): Promise<ResumenInscripcion[]> {
    return firstValueFrom(
      this.http.get<ResumenInscripcion[]>('/api/inscripciones', {
        params: { estado: 'FICHA_PENDIENTE' },
      }),
    );
  }

  /** null = reenviada; si no, el motivo (ej. "Esperá un minuto antes de volver a enviarla"). */
  async reenviarInvitacion(id: number): Promise<string | null> {
    try {
      await firstValueFrom(
        this.http.post<void>(`/api/inscripciones/${id}/reenviar-invitacion`, {}),
      );
      return null;
    } catch (error) {
      if (error instanceof HttpErrorResponse && (error.status === 409 || error.status === 404)) {
        return error.error?.error ?? 'No se pudo reenviar';
      }
      throw error;
    }
  }

  ficha(id: number): Promise<FichaAlumno> {
    return firstValueFrom(this.http.get<FichaAlumno>(`/api/inscripciones/${id}/ficha`));
  }

  async guardarFicha(id: number, datos: DatosFicha): Promise<ResultadoFicha> {
    try {
      await firstValueFrom(this.http.put<void>(`/api/inscripciones/${id}/ficha`, datos));
      return { tipo: 'ok' };
    } catch (error) {
      return resultadoDeError(error);
    }
  }

  async enviarFicha(id: number, envio: EnvioFicha): Promise<ResultadoFicha> {
    try {
      const r = await firstValueFrom(
        this.http.post<{ estado: 'ACTIVO' | 'EN_REVISION' }>(
          `/api/inscripciones/${id}/ficha/enviar`,
          envio,
        ),
      );
      return { tipo: 'ok', estado: r.estado };
    } catch (error) {
      return resultadoDeError(error);
    }
  }
}
