import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Datos públicos del gimnasio del dominio (GET /api/gimnasio). */
export interface Gimnasio {
  slug: string;
  nombre: string;
  zonaHoraria: string;
}

/** Usuario conectado (GET /api/sesion/yo). */
export interface Usuario {
  email: string;
  nombre: string;
  gimnasio: string;
}

export type EstadoCarga = 'cargando' | 'listo' | 'gimnasio-desconocido';

/**
 * Estado de la sesión del usuario en el gimnasio del dominio.
 *
 * Nunca manda un id de gimnasio: el backend lo deduce del dominio por el que entró el navegador
 * (gym-norte.localhost, power-fit.localhost...). La cookie de sesión es HttpOnly: este código no la
 * ve ni la toca; el navegador la manda solo.
 *
 * Usa signals: cada vez que cambia un valor, Angular redibuja lo que depende de él (Angular 22 no
 * usa zone.js, así que las signals son la forma de avisarle).
 */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly http = inject(HttpClient);

  readonly estado = signal<EstadoCarga>('cargando');
  readonly gimnasio = signal<Gimnasio | null>(null);
  readonly bienvenida = signal('');
  readonly usuario = signal<Usuario | null>(null);
  readonly conectado = computed(() => this.usuario() !== null);

  /** Carga el gimnasio del dominio, el token CSRF y, si hay sesión, el usuario. */
  async iniciar(): Promise<void> {
    try {
      this.gimnasio.set(await firstValueFrom(this.http.get<Gimnasio>('/api/gimnasio')));
    } catch (error) {
      if (esEstado(error, 404)) {
        this.estado.set('gimnasio-desconocido');
        return;
      }
      throw error;
    }
    try {
      const respuesta = await firstValueFrom(
        this.http.get<{ mensaje: string }>('/api/gimnasio/bienvenida'),
      );
      this.bienvenida.set(respuesta.mensaje);
    } catch {
      this.bienvenida.set(''); // la bienvenida es decorativa: si falla, se sigue igual
    }
    await this.refrescarCsrf();
    await this.cargarUsuario();
    this.estado.set('listo');
  }

  /** Devuelve false si el correo o la clave no son válidos (el backend no dice cuál de los dos). */
  async ingresar(email: string, clave: string): Promise<boolean> {
    try {
      await firstValueFrom(this.http.post<void>('/api/sesion/ingresar', { email, clave }));
    } catch (error) {
      if (esEstado(error, 401) || esEstado(error, 400)) {
        return false;
      }
      throw error;
    }
    await this.refrescarCsrf(); // al ingresar, el backend cambia el token CSRF
    await this.cargarUsuario();
    return true;
  }

  async salir(): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/sesion/salir', {}));
    this.usuario.set(null);
    await this.refrescarCsrf(); // al salir también se descarta el token anterior
  }

  /** Pide la cookie __Host-XSRF-TOKEN; HttpClient la copia al header X-XSRF-TOKEN en cada POST. */
  private async refrescarCsrf(): Promise<void> {
    await firstValueFrom(this.http.get<void>('/api/sesion/csrf'));
  }

  private async cargarUsuario(): Promise<void> {
    try {
      this.usuario.set(await firstValueFrom(this.http.get<Usuario>('/api/sesion/yo')));
    } catch (error) {
      if (esEstado(error, 401)) {
        this.usuario.set(null);
        return;
      }
      throw error;
    }
  }
}

function esEstado(error: unknown, estado: number): boolean {
  return error instanceof HttpErrorResponse && error.status === estado;
}
