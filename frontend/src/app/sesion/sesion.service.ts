import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
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
  /** Solo para mostrar u ocultar menús: la seguridad real la decide el backend en cada pedido. */
  roles?: string[];
}

/** Roles que trabajan en el mostrador (ADR-0007 §3). */
const ROLES_DE_PERSONAL = ['ADMINISTRADOR', 'RECEPCION'];

export type EstadoCarga = 'cargando' | 'listo' | 'gimnasio-desconocido';

/**
 * Resultado de un intento de ingreso. Un "union type": TypeScript obliga a quien lo usa a contemplar
 * todos los casos, y solo el caso 'bloqueado' trae los segundos de espera.
 *
 * 'codigo-mfa' y 'configurar-mfa': la contraseña es correcta pero algún rol del usuario exige MFA
 * (ADR-0005). Todavía NO hay sesión autenticada: falta el segundo paso.
 */
export type ResultadoIngreso =
  | { tipo: 'ok' }
  | { tipo: 'invalido' }
  | { tipo: 'bloqueado'; segundos: number }
  | { tipo: 'codigo-mfa' }
  | { tipo: 'configurar-mfa' };

/** Resultado de enviar un código de MFA (de la app o de recuperación). */
export type ResultadoMfa =
  { tipo: 'ok' } | { tipo: 'invalido' } | { tipo: 'bloqueado'; segundos: number };

/** Datos para configurar la app de códigos: el QR se dibuja a partir de `uri`. */
export interface ConfiguracionMfa {
  secreto: string;
  uri: string;
}

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
  /** ¿Mostrar el menú de recepción? (el backend igual responde 403 si no corresponde) */
  readonly esPersonal = computed(() =>
    (this.usuario()?.roles ?? []).some((rol) => ROLES_DE_PERSONAL.includes(rol)),
  );
  private carga: Promise<void> | null = null;
  /**
   * Códigos de recuperación recién generados. Se guardan ANTES de cargar al usuario para que la
   * pantalla los muestre primero (si no, se vería un instante la bienvenida). Solo viven en memoria.
   */
  readonly codigosRecuperacion = signal<string[] | null>(null);

  /**
   * Espera a que termine la carga inicial. La usan los guards de rutas: si alguien entra directo a
   * /recepcion, hay que saber primero quién es antes de decidir si puede verla.
   */
  esperarCarga(): Promise<void> {
    return this.carga ?? this.iniciar();
  }

  /** Carga el gimnasio del dominio, el token CSRF y, si hay sesión, el usuario (una sola vez). */
  iniciar(): Promise<void> {
    this.carga ??= this.cargar();
    return this.carga;
  }

  private async cargar(): Promise<void> {
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

  /**
   * 'invalido' si el correo o la clave no son válidos (el backend no dice cuál de los dos);
   * 'bloqueado' si hubo demasiados intentos fallidos (429), con los segundos que indica Retry-After.
   */
  async ingresar(email: string, clave: string): Promise<ResultadoIngreso> {
    return this.primerFactor('/api/sesion/ingresar', { email, clave });
  }

  /**
   * Pide que le manden un código al correo (ADR-0006). Devuelve false solo si el correo es inválido:
   * el backend responde lo mismo exista o no, así que acá nunca se sabe si está registrado.
   */
  async solicitarCodigo(email: string): Promise<boolean> {
    try {
      await firstValueFrom(this.http.post<unknown>('/api/sesion/codigo/solicitar', { email }));
      return true;
    } catch (error) {
      if (esEstado(error, 400)) {
        return false;
      }
      throw error;
    }
  }

  /** Ingresa con el código que llegó por correo. Mismas respuestas que con la contraseña. */
  async ingresarConCodigo(email: string, codigo: string): Promise<ResultadoIngreso> {
    return this.primerFactor('/api/sesion/codigo/ingresar', { email, codigo });
  }

  /**
   * Contraseña o código por correo: el backend responde igual en los dos casos (ADR-0006 §2).
   * 204 = adentro; 200 con "paso" = falta el segundo factor; 401/400 = inválido; 429 = bloqueado.
   */
  private async primerFactor(url: string, cuerpo: object): Promise<ResultadoIngreso> {
    let respuesta: HttpResponse<{ paso?: string } | null>;
    try {
      // observe: 'response' para ver el código HTTP, no solo el cuerpo.
      respuesta = await firstValueFrom(
        this.http.post<{ paso?: string } | null>(url, cuerpo, { observe: 'response' }),
      );
    } catch (error) {
      if (esEstado(error, 401) || esEstado(error, 400)) {
        return { tipo: 'invalido' };
      }
      if (error instanceof HttpErrorResponse && error.status === 429) {
        return { tipo: 'bloqueado', segundos: segundosDeEspera(error) };
      }
      throw error;
    }
    const paso = respuesta.body?.paso;
    if (paso === 'CODIGO_MFA') {
      return { tipo: 'codigo-mfa' };
    }
    if (paso === 'CONFIGURAR_MFA') {
      return { tipo: 'configurar-mfa' };
    }
    await this.alCompletarIngreso();
    return { tipo: 'ok' };
  }

  /** Segundo paso: código de 6 dígitos de la app, o un código de recuperación. */
  async verificarMfa(codigo: string): Promise<ResultadoMfa> {
    const error = await this.postMfa('/api/sesion/mfa/verificar', { codigo });
    if (error) {
      return error;
    }
    await this.alCompletarIngreso();
    return { tipo: 'ok' };
  }

  /** Primera vez: pide un secreto nuevo para dibujar el QR. null si el ingreso pendiente venció. */
  async iniciarConfiguracionMfa(): Promise<ConfiguracionMfa | null> {
    try {
      return await firstValueFrom(
        this.http.post<ConfiguracionMfa>('/api/sesion/mfa/configurar', {}),
      );
    } catch (error) {
      if (esEstado(error, 401)) {
        return null;
      }
      throw error;
    }
  }

  /**
   * Confirma la configuración con el primer código. Si es correcto deja los códigos de recuperación
   * en {@link codigosRecuperacion}: es la ÚNICA vez que el servidor los muestra.
   */
  async confirmarMfa(codigo: string): Promise<ResultadoMfa> {
    try {
      const respuesta = await firstValueFrom(
        this.http.post<{ codigosRecuperacion: string[] }>('/api/sesion/mfa/confirmar', { codigo }),
      );
      this.codigosRecuperacion.set(respuesta.codigosRecuperacion);
      await this.alCompletarIngreso();
      return { tipo: 'ok' };
    } catch (error) {
      const conocido = errorDeMfa(error);
      if (conocido) {
        return conocido;
      }
      throw error;
    }
  }

  /** Al quedar autenticado el backend cambia el token CSRF: hay que pedir el nuevo antes del próximo POST. */
  private async alCompletarIngreso(): Promise<void> {
    await this.refrescarCsrf();
    await this.cargarUsuario();
  }

  private async postMfa(
    url: string,
    cuerpo: object,
  ): Promise<Exclude<ResultadoMfa, { tipo: 'ok' }> | null> {
    try {
      await firstValueFrom(this.http.post<void>(url, cuerpo));
      return null;
    } catch (error) {
      const conocido = errorDeMfa(error);
      if (conocido) {
        return conocido;
      }
      throw error;
    }
  }

  /** El usuario confirmó que guardó los códigos: se borran de la memoria. */
  descartarCodigosRecuperacion(): void {
    this.codigosRecuperacion.set(null);
  }

  async salir(): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/sesion/salir', {}));
    this.usuario.set(null);
    this.codigosRecuperacion.set(null);
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

/** 401/400 → código inválido (o venció el paso); 429 → bloqueado. Cualquier otro error, desconocido. */
function errorDeMfa(error: unknown): Exclude<ResultadoMfa, { tipo: 'ok' }> | null {
  if (esEstado(error, 401) || esEstado(error, 400)) {
    return { tipo: 'invalido' };
  }
  if (error instanceof HttpErrorResponse && error.status === 429) {
    return { tipo: 'bloqueado', segundos: segundosDeEspera(error) };
  }
  return null;
}

/** Lee Retry-After (segundos). Si falta o no es un número, asume 60: mejor esperar de más que de menos. */
function segundosDeEspera(error: HttpErrorResponse): number {
  const valor = Number(error.headers.get('Retry-After'));
  return Number.isFinite(valor) && valor > 0 ? Math.ceil(valor) : 60;
}

function esEstado(error: unknown, estado: number): boolean {
  return error instanceof HttpErrorResponse && error.status === estado;
}
