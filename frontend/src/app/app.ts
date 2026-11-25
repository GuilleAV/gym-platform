import { Component, OnInit, inject, signal } from '@angular/core';
import { CodigosRecuperacion } from './sesion/codigos-recuperacion';
import { PasoClave } from './sesion/paso-clave';
import { PasoCodigoCorreo } from './sesion/paso-codigo-correo';
import { PasoCodigoMfa } from './sesion/paso-codigo-mfa';
import { PasoConfigurarMfa } from './sesion/paso-configurar-mfa';
import { PasoCorreo } from './sesion/paso-correo';
import { SesionService } from './sesion/sesion.service';

export { textoDeEspera } from './sesion/texto-de-espera';

/**
 * En qué paso del ingreso está el usuario (ADR-0006):
 * correo → código por correo (o contraseña) → segundo factor si el rol lo exige.
 */
type PasoIngreso = 'correo' | 'codigo-correo' | 'clave' | 'codigo-mfa' | 'configurar-mfa';

/**
 * Pantalla inicial del gimnasio: nombre, bienvenida e inicio de sesión.
 * Es la misma app para todos los gimnasios: el contenido depende del dominio.
 *
 * Solo ORQUESTA: decide qué paso mostrar. Cada paso es un componente con su propia lógica, así cada
 * uno se lee, se prueba y se cambia por separado.
 */
@Component({
  selector: 'app-root',
  imports: [
    PasoCorreo,
    PasoCodigoCorreo,
    PasoClave,
    PasoCodigoMfa,
    PasoConfigurarMfa,
    CodigosRecuperacion,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  protected readonly sesion = inject(SesionService);
  protected readonly paso = signal<PasoIngreso>('correo');
  /** El correo viaja entre pasos para no tener que escribirlo dos veces. */
  protected readonly email = signal('');

  ngOnInit(): void {
    void this.sesion.iniciar();
  }

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
