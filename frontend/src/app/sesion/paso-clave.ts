import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { textoDeEspera } from './texto-de-espera';
import { SesionService } from './sesion.service';

/** Ingreso con contraseña: opcional, para quien la prefiera (ADR-0006). */
@Component({
  selector: 'app-paso-clave',
  imports: [ReactiveFormsModule],
  template: `
    <form class="tarjeta" [formGroup]="formulario" (ngSubmit)="ingresar()">
      <h2>Ingresar con contraseña</h2>
      <label>
        Correo
        <input type="email" formControlName="email" autocomplete="username" inputmode="email" />
      </label>
      <label>
        Contraseña
        <input
          [type]="verClave() ? 'text' : 'password'"
          formControlName="clave"
          autocomplete="current-password"
        />
      </label>
      <button type="button" class="secundaria" (click)="verClave.set(!verClave())">
        {{ verClave() ? 'Ocultar contraseña' : 'Mostrar contraseña' }}
      </button>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
      <button type="submit" [disabled]="formulario.invalid || enviando()">Ingresar</button>
      <button
        type="button"
        class="secundaria"
        (click)="volver.emit(formulario.controls.email.value)"
      >
        Mejor mandame un código al correo
      </button>
    </form>
  `,
})
export class PasoClave implements OnInit {
  private readonly sesion = inject(SesionService);

  readonly emailInicial = input('');
  readonly segundoFactor = output<'codigo-mfa' | 'configurar-mfa'>();
  readonly volver = output<string>();

  /** Mostrar lo que se escribe: menos errores al tipear en el celular (NIST lo recomienda). */
  protected readonly verClave = signal(false);
  protected readonly error = signal('');
  protected readonly enviando = signal(false);
  protected readonly formulario = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    clave: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  ngOnInit(): void {
    this.formulario.controls.email.setValue(this.emailInicial());
  }

  protected async ingresar(): Promise<void> {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set('');
    const { email, clave } = this.formulario.getRawValue();
    try {
      const resultado = await this.sesion.ingresar(email.trim(), clave);
      switch (resultado.tipo) {
        case 'ok':
          this.formulario.reset();
          break;
        case 'invalido':
          this.error.set('Correo o contraseña incorrectos');
          break;
        case 'bloqueado':
          this.error.set(
            `Demasiados intentos fallidos. Probá de nuevo en ${textoDeEspera(resultado.segundos)}.`,
          );
          break;
        case 'codigo-mfa':
        case 'configurar-mfa':
          // La contraseña es correcta pero el rol exige MFA: se borra la clave y se pasa al segundo paso.
          this.formulario.controls.clave.reset();
          this.segundoFactor.emit(resultado.tipo);
          break;
      }
    } catch {
      this.error.set('No se pudo conectar con el servidor. Probá de nuevo.');
    } finally {
      this.enviando.set(false);
    }
  }
}
