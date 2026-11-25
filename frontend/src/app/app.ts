import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CodigosRecuperacion } from './sesion/codigos-recuperacion';
import { PasoCodigoMfa } from './sesion/paso-codigo-mfa';
import { PasoConfigurarMfa } from './sesion/paso-configurar-mfa';
import { SesionService } from './sesion/sesion.service';
import { textoDeEspera } from './sesion/texto-de-espera';

export { textoDeEspera } from './sesion/texto-de-espera';

/** En qué paso del ingreso está el usuario. */
type PasoIngreso = 'clave' | 'codigo-mfa' | 'configurar-mfa';

/**
 * Pantalla inicial del gimnasio: nombre, bienvenida e inicio de sesión.
 * Es la misma app para todos los gimnasios: el contenido depende del dominio.
 */
@Component({
  selector: 'app-root',
  imports: [ReactiveFormsModule, PasoCodigoMfa, PasoConfigurarMfa, CodigosRecuperacion],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  protected readonly sesion = inject(SesionService);
  protected readonly error = signal('');
  protected readonly enviando = signal(false);
  protected readonly paso = signal<PasoIngreso>('clave');

  protected readonly formulario = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    clave: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  ngOnInit(): void {
    void this.sesion.iniciar();
  }

  protected async ingresar(): Promise<void> {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set('');
    const { email, clave } = this.formulario.getRawValue();
    try {
      const resultado = await this.sesion.ingresar(email, clave);
      switch (resultado.tipo) {
        case 'ok':
          this.formulario.reset();
          break;
        case 'codigo-mfa':
        case 'configurar-mfa':
          // La contraseña es correcta pero el rol exige MFA: se borra la clave del formulario y se
          // pasa al segundo paso. La sesión todavía no está autenticada.
          this.formulario.controls.clave.reset();
          this.paso.set(resultado.tipo);
          break;
        case 'invalido':
          this.error.set('Correo o contraseña incorrectos');
          break;
        case 'bloqueado':
          this.error.set(
            `Demasiados intentos fallidos. Probá de nuevo en ${textoDeEspera(resultado.segundos)}.`,
          );
          break;
      }
    } catch {
      this.error.set('No se pudo conectar con el servidor. Probá de nuevo.');
    } finally {
      this.enviando.set(false);
    }
  }

  protected volverAClave(): void {
    this.paso.set('clave');
  }

  protected async salir(): Promise<void> {
    await this.sesion.salir();
    this.paso.set('clave');
  }
}
