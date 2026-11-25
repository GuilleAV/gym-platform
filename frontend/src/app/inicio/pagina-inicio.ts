import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CodigosRecuperacion } from '../sesion/codigos-recuperacion';
import { PasoClave } from '../sesion/paso-clave';
import { PasoCodigoCorreo } from '../sesion/paso-codigo-correo';
import { PasoCodigoMfa } from '../sesion/paso-codigo-mfa';
import { PasoConfigurarMfa } from '../sesion/paso-configurar-mfa';
import { PasoCorreo } from '../sesion/paso-correo';
import { SesionService } from '../sesion/sesion.service';

/**
 * En qué paso del ingreso está el usuario (ADR-0006):
 * correo → código por correo (o contraseña) → segundo factor si el rol lo exige.
 */
type PasoIngreso = 'correo' | 'codigo-correo' | 'clave' | 'codigo-mfa' | 'configurar-mfa';

/**
 * Página de inicio (/): el ingreso y, ya adentro, el saludo con los accesos según el rol.
 * Solo ORQUESTA: cada paso del ingreso es un componente con su propia lógica.
 */
@Component({
  selector: 'app-pagina-inicio',
  imports: [
    RouterLink,
    PasoCorreo,
    PasoCodigoCorreo,
    PasoClave,
    PasoCodigoMfa,
    PasoConfigurarMfa,
    CodigosRecuperacion,
  ],
  template: `
    <!-- Orden importante: los códigos de recuperación se muestran UNA vez, antes que nada. -->
    @if (sesion.codigosRecuperacion(); as codigos) {
      <app-codigos-recuperacion
        [codigos]="codigos"
        (listo)="sesion.descartarCodigosRecuperacion(); irA('correo')"
      />
    } @else if (sesion.usuario(); as usuario) {
      <section class="tarjeta">
        <p>
          Hola, <strong>{{ usuario.nombre }}</strong>
        </p>
        <p class="secundario">{{ usuario.email }}</p>
        @if (sesion.esPersonal()) {
          <a routerLink="/recepcion">Ir a recepción</a>
        }
        <button type="button" (click)="salir()">Cerrar sesión</button>
      </section>
    } @else {
      @switch (paso()) {
        @case ('codigo-correo') {
          <app-paso-codigo-correo
            [email]="email()"
            (segundoFactor)="irA($event)"
            (usarClave)="irA('clave')"
            (volver)="irA('correo')"
          />
        }
        @case ('clave') {
          <app-paso-clave
            [emailInicial]="email()"
            (segundoFactor)="irA($event)"
            (volver)="irA('correo', $event)"
          />
        }
        @case ('codigo-mfa') {
          <app-paso-codigo-mfa (volver)="irA('correo')" />
        }
        @case ('configurar-mfa') {
          <app-paso-configurar-mfa (volver)="irA('correo')" />
        }
        @default {
          <app-paso-correo
            [emailInicial]="email()"
            (codigoEnviado)="irA('codigo-correo', $event)"
            (usarClave)="irA('clave', $event)"
          />
        }
      }
    }
  `,
})
export class PaginaInicio {
  protected readonly sesion = inject(SesionService);
  protected readonly paso = signal<PasoIngreso>('correo');
  /** El correo viaja entre pasos para no tener que escribirlo dos veces. */
  protected readonly email = signal('');

  protected irA(paso: PasoIngreso, email?: string): void {
    if (email !== undefined) {
      this.email.set(email);
    }
    this.paso.set(paso);
  }

  protected async salir(): Promise<void> {
    await this.sesion.salir();
    this.paso.set('correo');
  }
}
